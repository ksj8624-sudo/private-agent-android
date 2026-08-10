# 🤖 Private Agent — Android Client

Private Agent 생태계의 Android Native Client입니다. Kotlin + Jetpack Compose로 작성되었으며, `private-agent-backend`의 REST API를 직접 호출해 개발 계획 생성과 코드 리뷰(Agent 실행) 기능을 제공합니다.

---

## Project Overview

- **private-agent의 역할**: Private Agent 생태계에서 **모바일(Android) Client**를 담당하는 프로젝트입니다. 사용자가 Android 기기에서 AI 개발 계획 생성, 코드 리뷰(Agent 실행) 기능을 사용할 수 있도록 네이티브 UI를 제공합니다.
- **Android Client의 목적**: 서버 상태 확인, 주제 기반 개발 계획 생성(Plan), Workspace 기반 코드 리뷰 실행(Agent 실행)을 모바일 환경에서 수행할 수 있게 하는 것이 목적입니다.
- **private-agent-backend와의 직접 연동 구조**: `data/network/NetworkModule.kt`에서 Retrofit Client를 직접 구성하고, `data/remote/AgentApi.kt`가 정의한 Backend REST Endpoint(`/health`, `/api/plan`, `/dev/agent`)를 `Repository → ViewModel` 흐름으로 직접 호출합니다.
- **private-agent-server를 거치지 않는다는 점**: `private-agent-server`(Telegram Bot 서버)는 코드상 어디에서도 참조되지 않습니다. Android는 Telegram 서버를 경유하지 않고 `private-agent-backend`를 직접 호출하는 독립적인 Client입니다.
- **Web / iOS Client와의 관계**: `ai-agent-lab`(Web), `PrivateAgent`(iOS)는 동일한 `private-agent-backend`를 각자 독립적으로 호출하는 별도 저장소입니다. Android는 이들과 코드나 상태를 공유하지 않습니다.
- **현재 Android Architecture**: `UI(Composable Screen) → ViewModel → Repository → AgentApi(Retrofit) → private-agent-backend` 구조를 따릅니다. 별도의 DI 프레임워크(Hilt 등)는 사용하지 않으며, `NetworkModule` 싱글턴 객체와 ViewModel 내부에서의 직접 생성으로 의존성을 구성합니다.

---

## Current Features

실제 코드에 구현되어 있고, 화면에서 실행 가능한 기능만 정리합니다.

- **Login 화면 UI** — ID/Password 입력 폼 (`LoginActivity`)
- **서버 상태 확인** — Main 화면 진입 시 Backend `/health` 자동 호출 및 표시
- **Dev Plan 생성** — 주제를 입력해 `private-agent-backend`에 개발 계획 생성을 요청
- **Code Review 실행** — Workspace(Backend / Server / Front)를 선택하고 요청 내용을 입력해 Agent 기반 코드 리뷰를 요청
- **Navigation Compose 기반 화면 전환** — Main → Review / Plan 화면 이동
- **Loading / Error 상태 표시** — 요청 중 로딩 인디케이터, 실패 시 에러 메시지를 공통 컴포넌트로 표시

---

## Architecture

```text
UI
      ↓
ViewModel
      ↓
Repository
      ↓
AgentApi (Retrofit)
      ↓
private-agent-backend
      ↓
OpenAI / GitHub / SQLite
```

- **UI**: `ui/screen/*.kt` — Jetpack Compose로 작성된 Screen Composable
- **ViewModel**: `ui/viewmodel/*.kt` — `androidx.lifecycle.ViewModel` 상속, `viewModelScope`로 Coroutine 실행
- **Repository**: `data/repository/*.kt` — `AgentApi`를 감싸는 얇은 호출 계층 (`AiRequestRepository`, `HealthRepository`)
- **AgentApi**: `data/remote/AgentApi.kt` — Retrofit `interface`로 정의된 Backend 호출 계약
- **DI 없음**: `NetworkModule`이 `object ... by lazy`로 Retrofit/AgentApi 싱글턴을 제공하며, 각 Repository는 ViewModel 생성자 내부에서 `NetworkModule.agentApi`를 직접 주입받아 생성합니다. Hilt/Koin 등 DI 프레임워크나 UseCase 계층은 존재하지 않습니다.

---

## Screen / Navigation Flow

Activity 2개(`LoginActivity`, `MainActivity`)로 구성되며, 화면 전환은 Activity 전환과 Navigation Compose 두 단계로 이루어집니다.

```text
LoginActivity (Launcher Activity)
  - ID/Password 입력 UI
  - 로그인 버튼 클릭 시 별도 인증 호출 없이 MainActivity로 이동
      ↓ (Intent, finish)
MainActivity
  - NavHost(startDestination = "main")
      ├── "main"   → MainScreen   (서버 상태 표시, Code Review / Dev Plan 진입 카드)
      ├── "review" → ReviewScreen (Workspace 선택 + 리뷰 요청)
      └── "plan"   → PlanScreen   (주제 입력 + 계획 요청)
```

`MainScreen`의 두 카드(Code Review, Dev Plan)를 통해서만 `review`/`plan` 화면으로 진입할 수 있으며, 그 외 별도 화면(History, Ask, Agent 선택 화면 등)은 존재하지 않습니다.

---

## Backend Integration

Android는 `private-agent-backend`를 **직접 호출**하는 Client입니다. `private-agent-server`(Telegram Bot 서버)는 이 요청 흐름에 포함되지 않습니다.

- **Base URL 관리**: `data/config/ApiConfig.kt`에 `BASE_URL`이 문자열로 하드코딩되어 있습니다(로컬 개발 서버 IP). Build Variant/환경 변수 기반 분리는 되어 있지 않습니다.
- **Network Client**: `data/network/NetworkModule.kt`에서 `OkHttpClient` + `Retrofit`을 싱글턴으로 구성합니다. `kotlinx.serialization`(`Json.asConverterFactory`)을 컨버터로 사용합니다.
- **Timeout**: `connectTimeout` 30s, `writeTimeout` 30s, `readTimeout`/`callTimeout` 300s(Agent 실행처럼 오래 걸리는 요청을 고려한 설정).
- **비동기 처리**: Retrofit의 `suspend` 함수 + ViewModel의 `viewModelScope.launch`로 처리하며, 응답은 `runCatching { }.onSuccess { }.onFailure { }`로 분기합니다.
- **Error 처리**: 네트워크/HTTP 예외는 `onFailure`에서 잡아 `errorMessage`로 UI State에 반영하고 Logcat에 로그를 남깁니다. Response의 `ok` 필드가 `false`인 경우도 실패로 간주해 에러 메시지를 표시합니다.
- **Request/Response 구조**: `data/remote/dto/*.kt`에 `@Serializable` data class로 정의되어 있으며, Backend 응답 스키마(`ok`, `answer` 등)를 그대로 매핑합니다.
- **네트워크 보안**: `network_security_config.xml`에서 Cleartext(HTTP) 트래픽을 허용해 로컬 HTTP Backend 호출을 지원합니다.

---

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose (Material 3)
- **Architecture**: MVVM (UI → ViewModel → Repository → AgentApi)
- **Navigation**: Navigation Compose
- **Network**: Retrofit 3 + OkHttp + `kotlinx.serialization` (JSON Converter)
- **Async**: Kotlin Coroutines (`viewModelScope`)
- **State 관리**: `StateFlow`(`HealthViewModel`) 및 Compose `mutableStateOf` 기반 UiState(`PlanViewModel`, `ReviewViewModel`)
- **Build**: Android Gradle Plugin 9.3.0, Kotlin 2.2.10, `minSdk 27` / `targetSdk 37` / `compileSdk 37`

---

## Project Structure

사용되지 않는 기본 템플릿 리소스는 생략했습니다.

```text
app/src/main/
├── AndroidManifest.xml
└── java/com/example/privateagent/
    ├── data/
    │   ├── config/
    │   │   └── ApiConfig.kt          # Backend Base URL
    │   ├── network/
    │   │   └── NetworkModule.kt      # Retrofit / OkHttp 싱글턴
    │   ├── remote/
    │   │   ├── AgentApi.kt           # Backend 호출 계약 (Retrofit interface)
    │   │   └── dto/                  # Request/Response DTO
    │   │       ├── HealthResponse.kt
    │   │       ├── PlanRequest.kt / PlanResponse.kt
    │   │       └── ReviewRequest.kt / ReviewResponse.kt
    │   └── repository/
    │       ├── AiRequestRepository.kt  # Plan / Review 요청
    │       └── HealthRepository.kt     # 서버 상태 확인
    └── ui/
        ├── screen/
        │   ├── LoginActivity.kt      # 진입 Activity, Login UI (Authentication 예정)
        │   ├── MainActivity.kt       # NavHost 보유 Activity
        │   ├── MainScreen.kt         # 서버 상태 + 메뉴 진입
        │   ├── PlanScreen.kt         # Dev Plan 요청 화면
        │   └── ReviewScreen.kt       # Code Review 요청 화면
        ├── component/
        │   └── AiRequestComponent.kt # 공통 Header/Input/WorkspaceSelector/ResultSection
        ├── viewmodel/
        │   ├── HealthViewModel.kt
        │   ├── PlanViewModel.kt
        │   └── ReviewViewModel.kt
        └── theme/
            ├── Color.kt / Type.kt / Theme.kt  # Material3 기본 테마 (Dynamic Color 지원)
```

---

## API Overview

Android가 실제로 호출하는 Backend API만 정리했습니다.

| API | Method | Endpoint | Android 호출 위치 | 용도 |
| --- | --- | --- | --- | --- |
| Health Check | GET | `/health` | `HealthRepository.getHealth()` ← `HealthViewModel.checkServer()` (MainScreen 진입 시 자동 호출) | 서버 연결 상태 확인 |
| Plan 생성 | POST | `/api/plan` | `AiRequestRepository.requestPlan()` ← `PlanViewModel.requestPlan()` | 입력한 주제(topic) 기반 개발 계획 생성 |
| Agent 실행 (Review) | POST | `/dev/agent` | `AiRequestRepository.requestReview()` ← `ReviewViewModel.requestReview()` | 선택한 Workspace에 대해 Agent 기반 코드 리뷰 실행. Review Task |

---

## Getting Started

### 요구 사항

- Android Studio (AGP 9.3.0 / Kotlin 2.2.10 호환 버전)
- `minSdk 27` 이상 기기 또는 에뮬레이터
- 접근 가능한 `private-agent-backend` 서버 (동일 네트워크)

### 설정

1. Android Studio에서 프로젝트를 엽니다. `local.properties`의 `sdk.dir`은 자동으로 설정됩니다.
2. `app/src/main/java/com/example/privateagent/data/config/ApiConfig.kt`의 `BASE_URL`을 사용 중인 `private-agent-backend` 주소로 수정합니다.
3. `private-agent-backend`를 먼저 실행한 뒤, 같은 네트워크에서 Android 기기/에뮬레이터가 접근 가능한지 확인합니다.

### 실행

Android Studio의 Run 구성으로 `app` 모듈을 실행하거나, 아래 명령으로 Debug APK를 빌드할 수 있습니다.

```bash
./gradlew assembleDebug
```

---

## Current Implementation

### Implemented

- MVVM Architecture
- Retrofit Network Layer
- Repository Pattern
- Compose Navigation
- Common UI Components

---

## Roadmap

Android Client가 직접 담당할 향후 개선 사항입니다. Backend, Telegram Server, Web, iOS 자체 기능은 포함하지 않았습니다.

- Login Authentication
- JWT Authentication
- Agent Type 선택(Cursor / Codex / Claude)
- Ask 화면 추가
- Agent History 화면
- Settings 화면
- Environment Configuration
- Unit / UI Test
- Jenkins Build

---

## Related Projects

- **private-agent-backend** — Android가 직접 호출하는 Backend API 서버
- **ai-agent-lab** — React 기반 Web Client 및 전체 프로젝트 문서 관리
- **PrivateAgent** — iOS Client
- **private-agent-server** — Telegram Bot 서버. Android와 직접 통신하지 않으며 요청 흐름에 포함되지 않음

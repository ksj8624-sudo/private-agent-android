# Private Agent Android Client — 프로젝트 구조 분석

- 분석 대상: `/Users/kimseongjin/Desktop/workspace/private-agent`
- 분석 기준 시점: 2026-08-12, `main` 브랜치 (마지막 커밋 `9203df5 feat: initialize Android client`, 이후 `LoginActivity.kt`에 커밋되지 않은 변경 존재)
- 목적: 이후 각 Feature(우선 로그인 + Refresh Token) 작업에서 반복 분석 없이 참조할 수 있는 공통 기준 문서
- 원칙: 코드 미수정, 추측 금지, 근거 파일/함수명 명시, 민감정보 실값 미출력, 불확실한 내용은 "확인 필요"로 표기

---

## 1. 프로젝트 요약

Private Agent 생태계의 Android Native Client 단일 모듈 프로젝트다. Kotlin + Jetpack Compose(Material 3)로 작성되었고, `private-agent-backend` REST API를 `UI → ViewModel → Repository → AgentApi(Retrofit)` 흐름으로 직접 호출한다. 현재 코드에 실제로 구현되어 동작하는 기능은 3가지뿐이다: 서버 상태 확인(`/health`), 개발 계획 생성(`/api/plan`), 코드 리뷰 요청(`/dev/agent`). **로그인은 UI만 존재하고 실제 인증 호출은 없다** — `LoginScreen`의 로그인 버튼은 서버 호출 없이 곧바로 `MainActivity`로 이동한다(`LoginActivity.kt:41`, `LoginActivity.kt:51-55`).

별도의 DI 프레임워크(Hilt/Koin)가 없고, 인증·토큰 저장·세션 관리 관련 코드가 전무하다는 점이 이번 로그인+Refresh 작업의 출발선이다.

---

## 2. 기술 스택과 빌드 환경

| 항목 | 값 | 근거 |
|---|---|---|
| Android Gradle Plugin | 9.3.0 | `gradle/libs.versions.toml:2` |
| Kotlin | 2.2.10 | `gradle/libs.versions.toml:9` |
| Compose BOM | 2026.02.01 | `gradle/libs.versions.toml:10` |
| compileSdk | 37 (minorApiLevel 1) | `app/build.gradle.kts:9-13` |
| minSdk | 27 | `app/build.gradle.kts:17` |
| targetSdk | 37 | `app/build.gradle.kts:18` |
| Java 호환성 | 11 | `app/build.gradle.kts:33-34` |
| 모듈 구성 | 단일 모듈 `:app` (Android Studio 프로젝트, 별도 Workspace/멀티모듈 아님) | `settings.gradle.kts:26` |

**Build Type / Flavor**
- Product Flavor 없음. `debug`(암묵적 기본)와 `release` 두 Build Type만 존재.
- `release` 빌드는 `optimization { enable = false }`로 코드 축소/최적화가 꺼져 있다(`app/build.gradle.kts:26-30`). R8/ProGuard 규칙 파일도 프로젝트 내 확인되지 않음.
- 서명(signingConfig) 설정 없음 — 확인 필요(릴리즈 배포 파이프라인 별도 존재 여부).

**주요 외부 라이브러리와 용도** (`gradle/libs.versions.toml`, `app/build.gradle.kts:41-62`)
- `androidx.activity:activity-compose`, `androidx.compose.*` (Material3, UI, Tooling) — Compose UI
- `androidx.navigation:navigation-compose` 2.9.6 — 화면 전환
- `androidx.lifecycle:lifecycle-runtime-ktx` — ViewModel/Coroutine 연동
- `com.squareup.retrofit2:retrofit` 3.0.0, `retrofit2:converter-kotlinx-serialization` — 네트워크 클라이언트
- `org.jetbrains.kotlinx:kotlinx-serialization-json` 1.9.0 — 직렬화
- OkHttp는 별도 버전 명시 없이 Retrofit 3의 전이 의존성으로 사용됨(`data/network/NetworkModule.kt`에서 직접 `okhttp3.OkHttpClient` 사용) — 프로젝트가 명시적으로 버전을 고정하지 않음, 확인 필요
- 테스트: `junit`, `androidx.test.ext:junit`, `espresso-core`, `compose-ui-test-junit4` — 템플릿 수준만 존재, Mock 라이브러리(MockK/Mockito) 미도입
- DataStore, EncryptedSharedPreferences, Security-Crypto, Hilt/Koin, Coil, WorkManager 등은 의존성 목록에 **없음**

**환경 설정 방식 / Base URL / 민감정보 관리**
- 개발·스테이징·운영을 구분하는 Build Variant, BuildConfig 필드, `.env` 방식이 전혀 없다.
- Base URL은 `data/config/ApiConfig.kt`에 `object ApiConfig { const val BASE_URL = "..." }` 형태로 **로컬 사설 IP가 하드코딩**되어 있다(실값은 본 문서에 기재하지 않음. 필요 시 해당 파일 직접 확인).
- `local.properties`(`sdk.dir`만 포함, `.gitignore`로 VCS 제외됨)와 `gradle.properties`에는 민감정보 없음.
- 민감정보 보관용 저장소(Keystore, EncryptedSharedPreferences 등) 자체가 프로젝트에 아직 존재하지 않음 — 신규 구현 필요(§6, §14 참고).

---

## 3. 전체 아키텍처 및 주요 폴더 구조

```
app/src/main/java/com/example/privateagent/
├── data/
│   ├── config/ApiConfig.kt              # Base URL 상수
│   ├── network/NetworkModule.kt         # OkHttp + Retrofit 싱글턴
│   ├── remote/
│   │   ├── AgentApi.kt                  # Retrofit 인터페이스 (health/plan/dev-agent)
│   │   └── dto/                         # 요청·응답 DTO (@Serializable)
│   └── repository/
│       ├── AiRequestRepository.kt       # plan/review 호출 래퍼
│       └── HealthRepository.kt          # health 호출 래퍼
└── ui/
    ├── screen/
    │   ├── LoginActivity.kt             # LAUNCHER Activity, 로그인 UI
    │   ├── MainActivity.kt              # NavHost 호스트 Activity
    │   ├── MainScreen.kt / PlanScreen.kt / ReviewScreen.kt
    ├── component/AiRequestComponent.kt  # 공통 Composable(Header/Input/ResultSection/WorkspaceSelector)
    ├── viewmodel/
    │   ├── HealthViewModel.kt / PlanViewModel.kt / ReviewViewModel.kt
    └── theme/ (Color.kt / Theme.kt / Type.kt)
```

**아키텍처 패턴**: MVVM에 가까운 단순 3계층 — `UI(Composable) → ViewModel → Repository → AgentApi(Retrofit)`. Domain/UseCase 계층 없음. DTO가 곧 화면에서 쓰이는 모델이며(예: `PlanResponse`, `ReviewResponse`), 별도 Domain Model로의 매핑이 없다.

**의존 방향**: `ui` → `data`. `data.repository`는 `data.remote`(AgentApi)에만 의존하고, `data.remote`는 `data.remote.dto`에 의존. `NetworkModule`은 `ApiConfig`와 `AgentApi`에 의존하는 최하단 조립 지점.

**Dependency Injection**: 없음. `NetworkModule`이 `object ... by lazy`로 Retrofit/AgentApi 싱글턴을 제공하고(`data/network/NetworkModule.kt:12,20-27`), 각 ViewModel이 생성자 본문에서 `NetworkModule.agentApi`를 직접 참조해 Repository를 생성한다(`HealthViewModel.kt:13`, `PlanViewModel.kt:22`, `ReviewViewModel.kt:23`). Compose 쪽에서도 `viewModel()` 팩토리 기본값을 그대로 사용(`MainScreen.kt:30`, `PlanScreen.kt:33`, `ReviewScreen.kt:28`).

**공통 Interface/Base 클래스**: 없음. `AgentApi`가 유일한 인터페이스(Retrofit 계약)이며, Base Repository/Base ViewModel 등의 추상화는 존재하지 않는다. 3개 ViewModel이 각자 `mutableStateOf`/`StateFlow` 기반 UiState를 독립적으로 정의(중복 패턴, 공통 부모 없음).

---

## 4. 앱 실행과 화면 전환 흐름

**시작 지점**: `AndroidManifest.xml`에서 `LoginActivity`가 `MAIN`/`LAUNCHER`로 선언된 유일한 진입 Activity(`AndroidManifest.xml:18-29`). 커스텀 `Application` 클래스는 선언되어 있지 않다.

**초기 화면 결정 방식**: 없음. 현재 로그인 여부/토큰 유무를 판단하는 로직이 전혀 없고, 앱을 실행하면 무조건 `LoginScreen`이 뜬다. "이미 로그인된 상태면 바로 Main으로" 같은 분기는 구현되어 있지 않다 — 로그인 기능 추가 시 신규로 설계해야 하는 지점(§14).

**화면 구성**
```
LoginActivity (LAUNCHER)
  └─ LoginScreen: ID/PW TextField + "로그인" Button
       onClick → moveToMain() (LoginActivity.kt:51-55)
       → Intent(this, MainActivity::class.java) + finish()
                ↓
MainActivity
  └─ NavHost(startDestination = "main")   (MainActivity.kt:25-47)
       ├─ "main"   → MainScreen   (서버 상태 표시 + Code Review/Dev Plan 진입 카드)
       ├─ "review" → ReviewScreen
       └─ "plan"   → PlanScreen
```
- Fragment Navigation 없음, Navigation Compose만 사용. Bottom Navigation 없음. 딥링크/외부 라우팅 없음.
- **로그인 성공 후 이동할 기존 메인 화면**: `MainActivity`이며, 이동 방식은 `LoginActivity.moveToMain()`(`LoginActivity.kt:51-55`)의 `Intent` + `finish()` 패턴을 그대로 재사용하면 된다. 현재는 실제 인증 없이 버튼 클릭만으로 호출됨.
- `MainScreen`의 두 카드(Code Review, Dev Plan)를 통해서만 `review`/`plan`으로 진입 가능(`MainScreen.kt:69-81`). 그 외 화면 없음.

---

## 5. 네트워크 계층

**사용 기술**: Retrofit 3 + OkHttp(전이 의존성) + `kotlinx.serialization`. Ktor 없음.

**공통 API Client 구성**: `data/network/NetworkModule.kt`
- `OkHttpClient.Builder()`에 `connectTimeout=30s`, `writeTimeout=30s`, `readTimeout=300s`, `callTimeout=300s`만 설정(`NetworkModule.kt:13-18`). Agent 실행처럼 오래 걸리는 요청을 고려한 값.
- Interceptor/Authenticator/로깅(`HttpLoggingInterceptor`) **전혀 없음**.
- `Retrofit.Builder().baseUrl(ApiConfig.BASE_URL).client(okHttpClient).addConverterFactory(Json.asConverterFactory(...))`로 `AgentApi` 싱글턴 생성(`NetworkModule.kt:20-27`), `by lazy`로 최초 접근 시점에 초기화.

**Service Interface / Endpoint**: `data/remote/AgentApi.kt`
```
GET  health        → HealthResponse
POST dev/agent     → ReviewResponse   (body: ReviewRequest)
POST api/plan      → PlanResponse     (body: PlanRequest)
```
로그인/리프레시 엔드포인트는 아직 정의되어 있지 않음.

**DTO / Domain 분리**: 분리 없음. `data/remote/dto/*.kt`의 `@Serializable data class`가 요청·응답·화면 표시용 데이터를 겸한다(예: `PlanResponse.answer`를 `PlanViewModel`이 그대로 UiState에 담아 화면에 표시).

**직렬화**: `kotlinx.serialization`, `Json.asConverterFactory("application/json".toMediaType())`(`NetworkModule.kt:24`). Gson/Moshi 없음.

**공통 HTTP 헤더**: 없음. 헤더를 붙이는 Interceptor나 `@Header` 애노테이션이 코드 어디에도 없다 — 인증 헤더를 붙일 지점이 아직 마련되어 있지 않다는 뜻(§14).

**HTTP 상태 코드 / 오류 변환**: 별도 매핑 계층 없음. 각 ViewModel이 `runCatching { repository.xxx() }.onSuccess {}.onFailure { throwable -> ... }`로 직접 처리하고(`PlanViewModel.kt:41-80`, `ReviewViewModel.kt:45-84`, `HealthViewModel.kt:20-26`), `throwable.message`를 그대로 UI 에러 문자열로 사용한다. HTTP status와 network 예외(`IOException`)를 구분하는 로직이 없다. `ReviewViewModel.kt:13`에서 `retrofit2.HttpException`을 import하지만 실제로 사용하지 않는다(미사용 import) — 상태 코드 기반 분기가 필요한 시점(401 등)에 참고할 기존 패턴이 없다는 의미.

**타임아웃/재시도/중복 요청 방지**: 타임아웃만 설정되어 있고, 재시도 정책·중복 요청 방지(디바운스, 진행 중 요청 취소 등)는 구현되어 있지 않다. 다만 각 요청 버튼은 `uiState.isLoading`으로 `enabled`를 막아 UI 레벨에서 중복 클릭만 방지한다(`PlanScreen.kt:59`, `ReviewScreen.kt:64`).

**로깅과 민감정보 노출 가능성**: `Log.i("success", response.answer)`(`PlanViewModel.kt:44`, `ReviewViewModel.kt:48`), 실패 시 상세 스택 정보를 `Log.e`로 출력(`PlanViewModel.kt:63-72`, `ReviewViewModel.kt:67-76`), 그리고 **요청 객체 전체를 `println`으로 출력**(`PlanViewModel.kt:82`, `ReviewViewModel.kt:86`, `println("ReviewRequest = $request")`). 현재는 request DTO에 비밀번호 같은 민감 필드가 없어 문제 없지만, 로그인 DTO에 동일 로깅 패턴을 그대로 적용하면 비밀번호가 Logcat에 노출될 위험이 있다 — §11 위험 요소로 기록.

**Coroutine 취소와 네트워크 요청 취소 연결 여부**: `viewModelScope.launch { ... }`로 실행되므로 ViewModel이 `onCleared()`될 때 표준적으로 취소되지만(`PlanViewModel.kt:40`, `ReviewViewModel.kt:44`, `HealthViewModel.kt:18`), 화면 전환/재요청 시 이전 Job을 명시적으로 취소하는 로직은 없다(각 함수 호출마다 새 `launch`만 추가됨) — 연속 클릭 시 여러 Job이 동시에 살아있을 수 있음(단, 버튼이 `isLoading` 중 비활성화되므로 실사용 영향은 제한적).

---

## 6. 인증 및 로컬 저장 구조

**현재 로그인 화면과 처리 방식**: `LoginActivity.kt`의 `LoginScreen`(`LoginActivity.kt:58-92`)은 `userId`/`password`를 `remember { mutableStateOf("") }`로만 들고 있고, 어떤 Repository/ViewModel과도 연결되어 있지 않다. `onLoginClick = { moveToMain() }`(`LoginActivity.kt:41`)가 그대로 `Intent` 전환을 호출하므로, **입력값 검증도, 서버 호출도, 실패 처리도 없는 상태**다.

**인증/사용자 상태 관리 객체**: 없음. `AuthState`, `SessionManager`, `UserRepository` 등 어떤 형태로도 존재하지 않는다.

**Access/Refresh Token 저장**: DataStore, SharedPreferences, EncryptedSharedPreferences, Android Keystore 관련 코드/의존성이 프로젝트 전체에서 전혀 발견되지 않는다(`gradle/libs.versions.toml` 의존성 목록 및 전체 소스 검색 기준). 즉 토큰 저장을 위한 기존 공통 저장소가 **없다** — 신규로 만들어야 한다.

**인증 헤더 적용 위치**: 없음(§5). `NetworkModule.kt`의 `OkHttpClient.Builder()`가 헤더를 붙일 자연스러운 위치가 되며, 여기에 Interceptor를 추가하는 것이 기존 구조와 가장 잘 맞는다(§14).

**401 처리 / Refresh 연동 / 동시성 제어**: 전혀 구현되어 있지 않다. `okhttp3.Authenticator` 구현체도, 401을 특별히 분기하는 코드도 없다(§5의 `HttpException` 미사용 import가 유일한 흔적). 따라서 "동시 401 시 단일 Refresh", "원 요청 최대 1회 재시도" 요구사항은 100% 신규 설계가 필요하다.

**프로세스 재시작 시 초기 인증 상태 결정**: 없음. `LoginActivity`가 항상 LAUNCHER로 뜨고 조건 분기가 없으므로, 매 프로세스 재시작마다 로그인 화면부터 시작된다(§4).

**토큰 삭제/로그아웃 상태 전환 지점**: 없음(로그아웃 UI/로직 자체가 없음).

---

## 7. UI 및 상태 처리 규칙

**사용 범위**: 전체 화면이 Jetpack Compose(`@Composable`)이며, Activity는 `LoginActivity`/`MainActivity` 2개뿐이고 둘 다 `ComponentActivity` + `setContent {}` 진입점 역할만 한다. Fragment, XML View 기반 화면 없음(XML은 `res/values`, `res/xml`(백업/네트워크 보안 설정), 아이콘 리소스에만 존재).

**ViewModel/UiState 구성**: `HealthViewModel`은 `StateFlow<String>`(`HealthViewModel.kt:14-15`) 단일 값. `PlanViewModel`/`ReviewViewModel`은 각각 `PlanUiState`/`ReviewUiState` data class를 `mutableStateOf`로 들고 `uiState by mutableStateOf(...)` + `private set` 패턴 사용(`PlanViewModel.kt:14-25`, `ReviewViewModel.kt:15-25`). 공통 Base UiState/Base ViewModel 없이 3곳에서 유사 구조가 중복 정의됨.

**Flow 사용**: `StateFlow`는 `HealthViewModel`에서만 사용, 나머지는 Compose `mutableStateOf`. `SharedFlow`/`LiveData` 사용 없음.

**단발성 이벤트 vs 지속 상태**: 구분 없음. `errorMessage: String?`이 `UiState`의 지속 필드로 존재해서(`PlanUiState.errorMessage`, `ReviewUiState.errorMessage`), 별도로 지우기 전까지 화면 재구성 시에도 남아있는 구조다 — 로그인 실패 메시지도 같은 패턴을 그대로 쓰면 동일한 문제(예: 화면 재진입 시 과거 에러가 다시 보임)가 재현될 수 있다.

**입력 검증 위치**: UI 레벨에서만 최소한으로 수행 — `contents.isNotBlank()`로 버튼 `enabled` 제어(`PlanScreen.kt:59`, `ReviewScreen.kt:64`). `LoginScreen`은 어떤 입력 검증도 없다(`LoginActivity.kt:58-92`).

**로딩/성공/실패 표현**: `AiResultSection` 공통 컴포넌트가 `isLoading`(CircularProgressIndicator), `errorMessage`(에러 텍스트), `result`(성공 텍스트)를 한 곳에서 렌더링(`ui/component/AiRequestComponent.kt:94-145`). Snackbar/Toast/Dialog는 사용하지 않고 전부 화면 내 인라인 텍스트로 표시.

**공통 Composable / 디자인 시스템**: `ui/component/AiRequestComponent.kt`에 `AiRequestHeader`, `AiWorkspaceSelector`, `AiInputField`, `AiResultSection` 4개 공통 컴포넌트 존재. 별도 디자인 시스템 패키지나 Design Token 정의는 없음.

**Material / Theme**: Material 3(`MaterialTheme`, `androidx.compose.material3.*`), `PrivateAgentTheme`(`ui/theme/Theme.kt`)이 Dynamic Color(Android 12+, `dynamicColor=true` 기본값)와 Light/Dark ColorScheme을 지원(`Theme.kt:36-58`). `LoginScreen`은 테마 색상 대신 하드코딩된 `Color(0xFF15151B)` 배경을 직접 사용(`LoginActivity.kt:62`) — 테마 일관성 예외 사례.

**접근성/다국어**: `strings.xml`에 `app_name` 하나만 정의되어 있고 나머지 텍스트는 Composable 내부에 한글 문자열 하드코딩(`LoginScreen`, `MainScreen`, `PlanScreen`, `ReviewScreen` 전반). 다국어 리소스 분리, `contentDescription` 등 접근성 처리는 최소 수준(`Icon`의 `contentDescription = title` 정도, `MainScreen.kt:109`).

**화면 회전/프로세스 재생성 시 상태 복원**: `remember { mutableStateOf(...) }`로 관리되는 로컬 입력값(예: `LoginScreen`의 `userId`/`password`, `PlanScreen`/`ReviewScreen`의 `contents`)은 `rememberSaveable`이 아니므로 **프로세스 재생성(회전 포함, 구성에 따라) 시 유지되지 않을 수 있다**. ViewModel의 `uiState`는 ViewModel이 살아있는 동안(구성 변경 한정)만 유지되고 프로세스 종료 시 사라진다. `SavedStateHandle` 사용 없음 — 위험 요소로 기록.

---

## 8. 테스트, 빌드 및 품질 관리

- **Unit Test**: `app/src/test/java/com/example/privateagent/ExampleUnitTest.kt` — Android Studio 템플릿 기본 테스트(`2+2=4`)만 존재. Repository/ViewModel/네트워크 계층에 대한 실질적 테스트 없음.
- **UI Test**: `app/src/androidTest/java/com/example/privateagent/ExampleInstrumentedTest.kt` — 템플릿 기본 테스트(`packageName` 확인)만 존재.
- **Mock/Stub**: MockK, Mockito, Turbine 등 관련 의존성이 `gradle/libs.versions.toml`에 없음.
- **포맷터/Lint**: ktlint, detekt, spotless 등 설정 파일 없음(`.editorconfig` 등 미발견). 기본 AGP/Android Lint 외 커스텀 규칙 없음.
- **빌드/테스트 실행 명령**: `./gradlew :app:assembleDebug`, `./gradlew :app:testDebugUnitTest`, `./gradlew :app:connectedAndroidTest` — 실제 실행/검증은 이번 분석에서 수행하지 않음(요청 원칙상 코드 미실행/미수정 범위로 판단, 필요 시 별도 확인 요망).
- **CI/CD**: `.github/workflows` 등 CI 설정 파일 리포지토리 내 미발견 — 확인 필요(별도 사내 CI 존재 가능성 있음).
- **현재 확인되는 빌드 경고/실패**: 없음. 이전 분석 시점(2026-08-12 최초 작성)에는 `LoginActivity.kt:47-48`에 커밋되지 않은 상태로 `val s1 = input[0]`, `print()`가 남아 있어 컴파일 실패가 예상되었으나, 해당 코드는 이후 삭제되었다(현재 `git diff` 기준으로 빈 줄 하나만 추가된 상태). `JAVA_HOME=/Applications/Android Studio.app/Contents/jbr/Contents/Home ./gradlew :app:compileDebugKotlin` 직접 실행으로 `BUILD SUCCESSFUL` 확인 완료(2026-08-12).

---

## 9. 외부 시스템과의 연결 지점

- **Backend 연결**: `private-agent-backend`(REST API)만 직접 호출. `data/config/ApiConfig.kt` → `data/network/NetworkModule.kt` → `data/remote/AgentApi.kt`가 유일한 접점.
- **private-agent-server(Telegram Bot 서버)**: 코드 어디에도 참조 없음 — README에도 명시적으로 "경유하지 않는다"고 기술됨(`README.md`).
- **iOS/Web 클라이언트**: 워크스페이스에 `ios`, `ai-agent-lab` 디렉터리가 각각 별도 저장소로 존재하나, 이번 분석 범위(`private-agent` 내부)에 따라 해당 저장소의 코드는 열어보지 않았다. README 기준으로는 각자 독립적으로 동일한 backend를 호출하며 Android와 코드/상태를 공유하지 않는다고 기술되어 있음 — **로그인/토큰 필드명, 만료 정책, 에러 코드 규격의 실제 일치 여부는 확인 필요**(backend/iOS 레포를 별도로 확인해야 함).
- **플랫폼별로 달라질 수 있는 부분**: 토큰 저장 방식(Android는 Keystore 기반 EncryptedSharedPreferences/DataStore, iOS는 Keychain — 코드 공유 불가), 401 재시도 처리(Android는 OkHttp Authenticator, iOS는 URLSession delegate 등 각자 구현), 프로세스 재시작 시 상태 복원 방식.
- **공통으로 맞춰야 할 규칙**: 로그인 요청/응답 필드명, Access/Refresh Token 수명, 401 응답 바디 포맷, 로그아웃/세션 만료 시 클라이언트 기대 동작 — 모두 backend 스펙 확인 필요.

---

## 10. 주요 파일과 역할

| 파일 | 역할 |
|---|---|
| `app/src/main/AndroidManifest.xml` | LAUNCHER Activity 선언, INTERNET 권한, cleartext 허용 |
| `data/config/ApiConfig.kt` | Base URL 상수 (환경 분리 없음) |
| `data/network/NetworkModule.kt` | OkHttp/Retrofit 싱글턴 조립 지점 — 인증 Interceptor/Authenticator 추가 시 핵심 수정 대상 |
| `data/remote/AgentApi.kt` | Retrofit 엔드포인트 계약 — 로그인/리프레시 엔드포인트 추가 대상 |
| `data/remote/dto/*.kt` | 요청/응답 DTO (도메인 모델과 미분리) |
| `data/repository/HealthRepository.kt`, `AiRequestRepository.kt` | AgentApi 얇은 래퍼 — AuthRepository 추가 시 참고할 기존 패턴 |
| `ui/screen/LoginActivity.kt` | 로그인 UI + `moveToMain()` 네비게이션 — 이번 작업의 핵심 수정 대상(컴파일 확인 완료) |
| `ui/screen/MainActivity.kt` | NavHost 호스트, 화면 라우팅 정의 |
| `ui/viewmodel/HealthViewModel.kt`, `PlanViewModel.kt`, `ReviewViewModel.kt` | 기존 ViewModel 패턴(UiState + runCatching) — LoginViewModel 설계 시 참고 |
| `ui/component/AiRequestComponent.kt` | 공통 Composable(헤더/입력/결과 표시) |
| `ui/theme/Theme.kt` | Material3 테마, Dynamic Color |

---

## 11. 현재 구조의 위험 요소 및 기술 부채

1. ~~**`LoginActivity.kt:47-48` 컴파일 위험 코드**~~ — 해소됨. 이전 분석 시점에 존재하던 `val s1 = input[0]`, `print()`(정의되지 않은 `input` 참조로 컴파일 실패 예상)는 이후 삭제되었고, `./gradlew :app:compileDebugKotlin` 실행으로 `BUILD SUCCESSFUL` 확인 완료(2026-08-12). 더 이상 위험 요소 아님.
2. **인증/토큰 관련 코드 전무** — 저장소, 헤더 첨부, 401 처리, 세션 상태 관리가 모두 신규 구현 대상.
3. **DI 부재** — `NetworkModule.agentApi`를 각 ViewModel이 직접 참조(`HealthViewModel.kt:13`, `PlanViewModel.kt:22`, `ReviewViewModel.kt:23`)해 테스트 시 Mock 주입이 어려움. Custom `Application` 클래스도 없어 `Context`가 필요한 컴포넌트(토큰 저장소 등)를 초기화할 자연스러운 지점이 없음.
4. **에러 메시지 그대로 노출** — `throwable.message`를 UI 문자열로 직접 사용(`PlanViewModel.kt:77`, `ReviewViewModel.kt:81`). 인증 실패 메시지 설계 시 서버 원문 노출 여부를 별도로 판단해야 함.
5. **요청/응답 로깅 위험 패턴** — `println("...Request = $request")`(`PlanViewModel.kt:82`, `ReviewViewModel.kt:86`), `Log.i(..., response.answer)`(`PlanViewModel.kt:44`, `ReviewViewModel.kt:48`). 로그인 DTO에 비밀번호 필드가 생기면 동일 패턴을 절대 그대로 적용하면 안 됨.
6. **Base URL 환경 미분리** — dev/staging/prod 전환 수단이 없어(`data/config/ApiConfig.kt`), 로그인 붙이기 전 최소한 debug/release Build Type별 분리 여부를 판단할 필요.
7. **전역 Cleartext 허용** — `AndroidManifest.xml:15-16`, `network_security_config.xml`에서 모든 도메인에 대해 평문 HTTP 허용. 토큰이 오가는 로그인 트래픽 특성상 위험도가 높아짐(수정은 이번 범위 아님, 기록만).
8. **release 빌드 최적화 비활성화** — `optimization { enable = false }`(`app/build.gradle.kts:26-30`)로 코드 축소/난독화 미적용. 토큰 저장 로직 추가 시 위험도 증가 요인.
9. **테스트/CI 부재** — Mock 라이브러리 미도입, 템플릿 테스트만 존재, CI 설정 미발견. 인증처럼 실패 시나리오가 중요한 로직에 회귀 방지 수단이 없음.
10. **UiState의 errorMessage가 일회성 이벤트가 아님** — 화면 재진입 시 과거 에러가 남아있을 수 있는 구조(`PlanUiState`, `ReviewUiState`). 로그인 실패 메시지 설계 시 동일한 문제 재현 가능성.
11. **입력 상태 미저장** — `remember`(비-`rememberSaveable`) 기반 입력값은 프로세스 재생성 시 유실 가능(`LoginActivity.kt:60-61` 등).
12. **미사용 import로 남은 흔적** — `ReviewViewModel.kt:13`의 `retrofit2.HttpException`이 실제로는 사용되지 않음. 상태 코드 기반 분기 로직이 한 번도 만들어진 적이 없다는 정황적 증거.
13. **릴리즈 서명 설정 확인 불가** — `app/build.gradle.kts`에 `signingConfigs` 없음. 배포 파이프라인 존재 여부 확인 필요.

---

## 12. 이후 Feature 작업 시 지켜야 할 규칙

- 현재 코드 스타일을 우선 기준으로 따른다: `object ... by lazy` 싱글턴 패턴(`NetworkModule`), `data class UiState + mutableStateOf + private set` ViewModel 패턴, `runCatching{}.onSuccess{}.onFailure{}` 에러 처리 패턴, Repository는 AgentApi를 감싸는 얇은 래퍼로 유지.
- DI 프레임워크를 새로 도입하지 않는 한(별도 논의 필요), 기존처럼 생성자에서 `NetworkModule.agentApi`를 직접 참조하는 방식을 유지한다.
- 도메인 모델 계층을 새로 만들지 말고 기존처럼 `@Serializable` DTO를 화면까지 그대로 사용하는 흐름을 따른다(단, 인증 토큰처럼 민감한 데이터는 UiState에 직접 담지 않도록 예외적으로 주의).
- 새 화면은 기존처럼 `Composable + Preview` 쌍으로 작성하고, 공통 UI는 `ui/component/AiRequestComponent.kt`에 있는 컴포넌트를 재사용하거나 같은 위치에 추가한다.
- 요청/응답 객체를 로그로 남길 때는 §11-5의 위험 패턴을 반복하지 말고, 민감 필드(비밀번호, 토큰)는 로깅 대상에서 제외한다.
- 이번 분석 문서에 기록된 위험 요소(§11)는 이번 단계에서 리팩터링하지 않되, 새로 작성하는 코드가 같은 문제를 반복하지 않도록 한다.

---

## 13. 추가 확인이 필요한 항목

- `private-agent-backend`의 실제 로그인/리프레시 엔드포인트 경로, 요청/응답 필드명, Access/Refresh Token 만료 정책, 401 응답 바디 포맷 — 백엔드 레포 별도 확인 필요(이번 분석 범위 밖).
- Refresh Token 자체가 만료/폐기(revoke)되었을 때 백엔드가 내려주는 상태 코드와 바디 포맷.
- OkHttp의 실제 적용 버전(Retrofit 3.0.0의 전이 의존성, 프로젝트에서 명시적으로 고정하지 않음).
- 릴리즈 서명 설정 및 배포 파이프라인 존재 여부.
- CI/CD, 코드 포맷터/Lint 관련 사내 별도 문서·설정 존재 여부.
- `minSdk 27` 환경에서 DataStore/EncryptedSharedPreferences + Android Keystore 조합이 문제없이 동작하는지(대체로 지원되나 최신 AndroidX Security 라이브러리 요구사항 재확인 필요).
- iOS(`PrivateAgent`)/Web(`ai-agent-lab`) 클라이언트와 인증 에러 코드·필드명 규격이 실제로 일치하는지.

---

## 14. 로그인 + Refresh 구현을 위한 권장 순서

완성 코드는 작성하지 않고, 설계 관점의 순서와 위치만 제시한다.

### 재사용할 기존 구성요소
- `data/network/NetworkModule.kt`의 싱글턴 조립 패턴 — `OkHttpClient.Builder()` 체인에 Interceptor/Authenticator를 추가하는 확장 지점으로 그대로 활용.
- `data/remote/AgentApi.kt`의 `@GET/@POST + suspend fun` 스타일 — 로그인/리프레시 엔드포인트도 동일 스타일로 추가.
- `data/repository/HealthRepository.kt` 스타일의 얇은 Repository 패턴 — `AuthRepository`도 동일하게 AgentApi를 감싸는 형태로 작성.
- `PlanUiState`/`ReviewUiState` + `mutableStateOf` + `private set` ViewModel 패턴 — `LoginUiState`/`LoginViewModel`에 동일 적용.
- `LoginActivity.moveToMain()`(`LoginActivity.kt:51-55`) — 로그인 성공 후 MainActivity 이동은 이 메서드를 그대로 재사용.
- `LoginScreen`의 기존 UI 레이아웃(`LoginActivity.kt:58-92`) — TextField/Button 구조 유지, `onLoginClick`의 동작만 실제 인증 호출로 교체.

### 새로 추가할 것으로 예상되는 파일
- `data/remote/dto/LoginRequest.kt`, `LoginResponse.kt`(Access/Refresh Token 포함) — 실제 필드명은 백엔드 확인 후 확정(§13).
- `data/remote/dto/RefreshRequest.kt`, `RefreshResponse.kt` — 백엔드가 refresh를 별도 엔드포인트로 제공하는지 확인 후 결정.
- `data/repository/AuthRepository.kt` — 로그인/리프레시 호출 래퍼.
- `data/auth/TokenStore.kt`(가칭) — DataStore 또는 EncryptedSharedPreferences + Android Keystore 기반 토큰 저장소. Access/Refresh를 개별 저장 메서드로 노출하지 말고 `saveTokenPair(access, refresh)` 단일 메서드로 원자성을 보장하는 형태 권장.
- `data/auth/SessionManager.kt` 또는 `AuthState.kt`(가칭) — `StateFlow<AuthState>`(예: `Unknown`/`LoggedIn`/`LoggedOut`)로 인증 상태를 노출, 로그아웃(토큰 삭제 포함)을 한 곳으로 모으는 지점.
- `data/network/AuthInterceptor.kt` — 매 요청에 Authorization 헤더 첨부.
- `data/network/TokenAuthenticator.kt` — `okhttp3.Authenticator` 구현, 401 처리 및 단일 Refresh 보장.
- `ui/viewmodel/LoginViewModel.kt` — 로그인 폼 상태 + 제출 로직.
- (Context가 필요한 `TokenStore` 초기화를 위해) `PrivateAgentApplication.kt` 신규 도입 여부 검토 — 현재 커스텀 `Application` 클래스가 없어 초기화 지점이 마땅치 않음(§11-3).

### 수정할 것으로 예상되는 기존 파일
- `ui/screen/LoginActivity.kt` — 47-48행 정리(최우선), `LoginViewModel` 연동, 앱 시작 시 기존 세션 유무 확인 로직 추가.
- `data/network/NetworkModule.kt` — `AuthInterceptor`/`TokenAuthenticator`를 `OkHttpClient.Builder()`에 연결, `TokenStore` 참조 방식 결정.
- `data/remote/AgentApi.kt` — 로그인/리프레시 엔드포인트 추가.
- `AndroidManifest.xml` — 커스텀 `Application` 클래스 도입 시 `android:name` 등록.
- `app/build.gradle.kts`, `gradle/libs.versions.toml` — DataStore 또는 `androidx.security:security-crypto` 의존성 추가.

### 단계별 순서, 선행 조건, 완료 기준

1. ~~**`LoginActivity.kt` 컴파일 위험 코드 정리**~~ — 완료. `./gradlew :app:compileDebugKotlin` 성공 확인(2026-08-12).
2. **백엔드 로그인/리프레시 API 계약 확인**(§13) — 완료 기준: 엔드포인트 경로, 요청/응답 필드, 401/만료 처리 방식 문서화 또는 확답 확보.
3. **DTO/AgentApi 확장** — 선행 조건: 2 완료. 완료 기준: 컴파일 성공 + 실제 서버 대상 수동 호출(curl/Postman 등)로 응답 스키마 확인.
4. **TokenStore 구현**(신규 의존성 추가 포함) — 완료 기준: 저장/조회/삭제 단위 테스트 통과.
5. **AuthRepository 구현 — 토큰 쌍 저장 및 부분 실패 롤백** — 로그인 API 응답으로 access/refresh를 받은 즉시 `TokenStore.saveTokenPair(access, refresh)` 단일 트랜잭션으로 저장하고, 저장 자체가 실패하면(예: Keystore 접근 오류) 이미 부분적으로 쓰여진 값을 같은 메서드 내부에서 롤백(clear)하는 책임을 `TokenStore`에 둔다 — Repository/ViewModel이 access/refresh를 따로따로 저장하지 않도록 하는 것이 핵심.
6. **SessionManager/AuthState 구현 — 인증 상태 초기화 순서** — 앱 프로세스 시작 시 `AuthState`를 `Unknown`으로 시작 → `TokenStore`에서 저장된 토큰 유무를 1회 확인(가능하면 앱 시작 초반, `LoginActivity.onCreate` 또는 진입 시점) → `LoggedIn`/`LoggedOut`으로 전이. `LoginActivity`는 이 상태를 관찰해 `LoggedIn`이면 즉시 `moveToMain()` 재사용, 그 외에는 로그인 폼을 그대로 노출.
7. **인증 헤더 적용 위치** — `NetworkModule.kt`의 `OkHttpClient.Builder()`에 `AuthInterceptor` 추가. 인터셉터는 동기 컨텍스트이므로 `TokenStore`가 노출하는 캐시된(in-memory) access token 값을 읽어 `Authorization` 헤더를 붙이는 방식을 권장.
8. **동시 401 및 단일 Refresh 처리 위치** — `TokenAuthenticator`(okhttp3.Authenticator) 내부에서 Mutex(또는 synchronized)로 감싸 여러 요청이 동시에 401을 받아도 refresh 호출은 한 번만 실행되도록 하고, 대기 중이던 나머지 요청은 갱신된 토큰으로 재사용한다. 원래 요청 재시도는 `Response.priorResponse` 체인 길이를 확인해 최대 1회로 제한(무한 루프 방지).
9. **Refresh 거절과 네트워크 오류 구분 위치** — 동일하게 `TokenAuthenticator` 내부: refresh 요청이 401/명시적 거절 응답을 받으면 `SessionManager`를 통해 `LoggedOut`으로 전이(토큰 삭제 포함). refresh 요청이 `IOException` 등 네트워크 오류로 실패하면 로그아웃시키지 않고 `null`을 반환해(재시도 안 함) 세션을 유지한 채 해당 요청만 실패로 남긴다.
10. **로그인 화면 연동 및 메인 이동** — `LoginViewModel.login(id, pw)` → 성공 시 `LoginActivity.moveToMain()`(기존 메서드 재사용) → 실패 시 기존 `AiResultSection`과 유사한 방식으로 에러 표시(단, §11-4/§11-10에서 지적한 "서버 메시지 그대로 노출", "에러 상태가 지속되는 문제"를 반복하지 않도록 주의).
11. **로그아웃/토큰 삭제 지점 정리** — `SessionManager`에 로그아웃 메서드를 하나로 통일해, 사용자 로그아웃(이번 범위에 없다면 후속 작업)과 Authenticator의 refresh 실패 양쪽 모두 이 메서드를 거치도록 한다.
12. **단계별 빌드 및 검증** — 매 단계 `./gradlew :app:compileDebugKotlin`, `./gradlew :app:testDebugUnitTest` 실행. 실기기/에뮬레이터에서 로그인 → (백엔드에서 access token 만료 시간을 짧게 설정해) 401 유도 → 자동 refresh 및 원 요청 재시도 확인 → refresh 자체 만료 시 로그아웃 전이 확인.

### iOS와 동작 규칙을 맞춰야 하는 부분
- 로그인 요청/응답 필드명, Access/Refresh Token 수명, 401/refresh 실패 응답 포맷 — backend 스펙 기준으로 통일(§13).
- 로그아웃/세션 만료 시 클라이언트가 기대해야 할 서버 동작(예: refresh token invalidate 여부).

### Android 구조에 맞게 별도로 구현해야 하는 부분
- 토큰 저장: iOS의 Keychain에 대응하는 Android Keystore 기반 저장소(EncryptedSharedPreferences 또는 DataStore + Keystore) — 코드 공유 불가, 신규 구현 필수.
- 401 처리: iOS의 `URLSession` 레벨 재시도 로직과 달리 Android는 OkHttp `Authenticator` 메커니즘을 사용 — 동시성 제어 방식도 플랫폼별로 다르게 구현.
- 프로세스 재시작 시 초기 인증 상태 판단: Android는 프로세스 종료 시 Activity/ViewModel 상태가 완전히 사라지므로, 앱 시작마다 `TokenStore`를 동기/비동기로 조회해 `AuthState`를 재구성하는 로직이 Android 쪽에만 필요.

# Android Authentication 재리뷰 결과

## 1. 재리뷰 일시와 범위

- 재리뷰 일시: 2026-08-27
- 범위: 1차 Review(`authentication-android-review.md`) 이후 Fix 작업으로 변경된 Android 인증 관련 코드 전체, 신규 유닛테스트 5개 파일(20 케이스), 빌드/테스트 결과, 사용자 실기기 검증 보고.
- 코드/테스트는 수정하지 않았고, 정적 코드 읽기 + 빌드/테스트 실행만 수행했다. Git commit/push는 수행하지 않았다.

---

## 2. 참고한 문서와 실제 코드

### 문서
- `/Users/kimseongjin/Desktop/workspace/ai-agent-lab/docs/prompts/tasks/review.md`
- `/Users/kimseongjin/Desktop/workspace/private-agent/docs/designs/android-login-auth.md`
- `/Users/kimseongjin/Desktop/workspace/private-agent/docs/architecture/android-project-overview.md`
- `/Users/kimseongjin/Desktop/workspace/private-agent/docs/reviews/authentication-android-review.md`(1차 Review)
- `/Users/kimseongjin/Desktop/workspace/private-agent/docs/workflows/bugfix/authentication-android-bugfix.md`(Fix Workflow — 워크플로가 지정한 경로 `docs/workflow/fix/...`는 실제로 `docs/workflows/bugfix/...`였음, 정확한 경로로 정정해 확인)
- `/Users/kimseongjin/Desktop/workspace/private-agent-backend/docs/backend-api-spec.xlsx`(1차 리뷰에서 이미 확인한 내용 재확인, 변경 없음)
- `/Users/kimseongjin/Desktop/workspace/ios/PrivateAgent/docs/reviews/authentication-ios-review.md`, `authentication-ios-rereview.md`

### 실제 코드 (Android, 전체 직접 재읽음)
`TokenAuthenticator.kt`, `AuthInterceptor.kt`, `AuthHeader.kt`, `NetworkModule.kt`, `data/auth/SessionManager.kt`, `AuthViewModel.kt`, `LoginErrorMapper.kt`, `LoginActivity.kt`, `MainActivity.kt`, `PrivateAgentApplication.kt`, `AndroidManifest.xml`, `TokenStore.kt`, `AgentApi.kt`, `AuthRepository.kt`, 인증 DTO 6개(`AuthErrorResponse`/`AuthUser`/`LoginRequest`/`LoginResponse`/`RefreshRequest`/`RefreshResponse`), 신규 유닛테스트 5개 파일 전체.

### 실제 코드 (Backend)
`src/index.js`, `src/routes/api.js`, `src/routes/auth.js`, `src/controllers/authController.js`, `src/services/authService.js`, `src/middleware/authMiddleware.js` — **이번 재리뷰에서 처음으로 Backend 저장소의 `git status`/`git diff`를 확인**(1차 리뷰에서는 파일 내용만 확인하고 git 상태는 확인하지 않았음). 그 결과 §9에 기록한 중요한 사실을 새로 발견했다.

---

## 3. 1차 Review 이후 git 변경 범위

Android 저장소(`private-agent`)의 `git status`/`git diff`/`git diff --cached`를 확인한 결과, 1차 Review 시점 대비 다음이 신규로 추가됐다(Fix Workflow가 의도한 범위와 정확히 일치).

**신규 파일**
- `app/src/main/java/com/example/privateagent/data/auth/SessionManager.kt`
- `app/src/main/java/com/example/privateagent/data/network/AuthHeader.kt`
- `app/src/main/java/com/example/privateagent/ui/viewmodel/LoginErrorMapper.kt`
- 유닛테스트 5개: `AuthHeaderTest.kt`, `AuthInterceptorExcludedPathsTest.kt`, `TokenAuthenticatorResponseCountTest.kt`, `SessionManagerRestoreLogicTest.kt`, `LoginErrorMapperTest.kt`

**수정 파일**: `TokenAuthenticator.kt`, `AuthInterceptor.kt`, `NetworkModule.kt`, `AuthViewModel.kt`, `LoginActivity.kt`, `MainActivity.kt`

1차 Review 이후 그 외의 파일(`TokenStore.kt`, `AgentApi.kt`, `AuthRepository.kt`, DTO 6개, `PrivateAgentApplication.kt`, `AndroidManifest.xml`, `app/build.gradle.kts`, `ApiConfig.kt`)은 **변경되지 않았다** — Fix 작업이 "Backend/iOS 코드 미수정, API Endpoint 미변경, DI 미도입"이라는 작업 제한을 지켰음을 git diff로 확인했다.

---

## 4. 1차 Review Issue 해결 검증

### C1. Bearer 접두사 공백 누락 — **해결**

- **근거**: `AuthHeader.kt:9` `const val BEARER_PREFIX = "Bearer "`(공백 포함). `AuthInterceptor.kt:26-27`과 `TokenAuthenticator.kt:31-32,48-51,84-87,102-106` 모두 이 상수 하나만 참조해 헤더를 만들고 토큰을 추출한다 — 두 파일이 서로 다른 리터럴을 쓰던 1차 버그 구조가 사라졌다.
- **비교 로직 검증**: `requestAccessToken = response.request.header(AuthHeader.NAME)?.removePrefix(AuthHeader.BEARER_PREFIX)`가 이제 공백까지 정확히 제거하므로, `currentAccessToken`(저장소 원문)과 순수 토큰 문자열끼리 비교된다. 최초 401(아직 아무도 갱신하지 않은 상태)에서 `currentAccessToken == requestAccessToken`이 성립해 "이미 갱신됨" 오판 없이 실제 Refresh 호출로 진행됨을 코드로 확인했다.
- **재시도 헤더**: Refresh 성공 시 `"${AuthHeader.BEARER_PREFIX}${body.accessToken}"` → `"Bearer <newAccessToken>"`(공백 정확) 형식으로 원 요청을 재시도한다.
- **테스트 근거**: `AuthHeaderTest`가 실제 `AuthHeader.BEARER_PREFIX`(운영 상수)를 대상으로 "공백 포함" 및 "부착→추출 왕복 시 토큰 값 불변"을 직접 검증한다. 내부 복제 로직이 아니라 운영 상수 자체를 assert한다.

### C2. 앱 인증 상태 복원 및 강제 로그아웃 화면 전환 — **해결**

- **상태 구분**: `data/auth/SessionManager.kt:8-12` — `sealed interface AuthState { Initializing, LoggedIn, LoggedOut }`으로 세 상태를 명확히 구분한다.
- **초기화 순서**: `PrivateAgentApplication.onCreate()`(`:8-9`) → `NetworkModule.initialize(applicationContext)`(`NetworkModule.kt:18-25`)가 `TokenStore` 생성 직후 동기적으로 `SessionManager.restore(tokenStore)`를 호출한다. Android는 `Application.onCreate()`가 모든 Activity의 `onCreate()`보다 항상 먼저 완료되도록 보장하므로, `LoginActivity`/`MainActivity`가 처음 컴포지션될 때 `SessionManager.authState`는 이미 `LoggedIn`/`LoggedOut`으로 확정돼 있다 — `Initializing`이 실제로 화면에 노출될 가능성은 사실상 없지만, `LoginActivity`는 방어적으로 `Initializing` 분기에서 로딩 인디케이터만 표시한다(`LoginActivity.kt:54-61`).
- **토큰 쌍 판단**: `SessionManager.kt:42-56`의 `isPartialTokenPair`/`decideRestoredState`가 "둘 다 있으면 LoggedIn", "하나만 있으면 LoggedOut + 즉시 `clearTokens()`", "둘 다 없으면 LoggedOut"을 정확히 구현한다.
- **로그인 성공 → LoggedIn**: `AuthViewModel.kt:56-77` — `tokenStore.saveTokens(...)`가 `true`를 반환한 뒤에만 `SessionManager.onLoginSuccess()`가 호출된다. 저장 실패 시 `throw`로 `onFailure`로 빠져 `onLoginSuccess()`가 호출되지 않는다.
- **명시적 거절 → LoggedOut**: `TokenAuthenticator.kt`에서 Refresh Token 없음(`:60-64`), 400/401(`:74-82`), 새 토큰 쌍 저장 실패(`:96-100`) 세 지점 모두 `tokenStore.clearTokens()` 직후 `SessionManager.onForcedLogout()`을 호출한다. 네트워크 오류·5xx·기타 4xx·디코딩 오류 경로(`:66-71`, `:74-83`의 else)는 토큰과 세션 상태를 건드리지 않고 `null`만 반환한다 — 명시적 거절과 시스템 오류가 정확히 분리되어 있다.
- **화면 전환**: `LoginActivity.kt:53-84` — `when(sessionState)`로 `LoggedIn`이면 `LaunchedEffect(Unit) { moveToMain() }`, `LoggedOut`이면 로그인 폼을 그린다. `MainActivity.kt:29-35` — `LaunchedEffect(sessionState) { if (sessionState == AuthState.LoggedOut) moveToLogin() }`이고 `moveToLogin()`(`:68-72`)이 `startActivity + finish()`를 수행한다.
- **중복 실행 방지**: `LaunchedEffect(Unit)`은 같은 컴포지션 슬롯에서 한 번만 실행되고, `MutableStateFlow`는 동일 값 재대입 시 재방출하지 않으므로(`onForcedLogout()`이 여러 401 경로에서 중복 호출돼도 안전), Compose 재구성이나 상태 재전달로 `moveToMain()`/`moveToLogin()`이 중복 실행되지 않는다.
- **테스트 근거**: `SessionManagerRestoreLogicTest`(6케이스)가 `decideRestoredState`/`isPartialTokenPair`(운영 함수, `internal`로 노출)를 4가지 토큰 조합 전부에 대해 직접 검증한다. 다만 `TokenStore`가 Android `Context`를 요구해 `restore(tokenStore)` 메서드 자체(부수효과 포함 전체 흐름)나 화면 전환 로직은 자동화 테스트가 아니라 코드 읽기로만 확인했다(§6에 기록).

### M1. 로그인/Refresh 요청의 Authenticator 재진입 — **해결**

- **근거**: `TokenAuthenticator.kt:17-22` — `authenticate()` 최상단에서 `path == AUTH_LOGIN_PATH || path == AUTH_REFRESH_PATH`이면 재시도 횟수 체크보다도 먼저 즉시 `null`을 반환한다. 로그인 401이 Refresh 대상으로 오인될 수 없다.
- **분리 구조 유지**: `NetworkModule.kt:32-36`의 `refreshClient`/`refreshApi`는 여전히 Interceptor/Authenticator가 전혀 붙지 않은 별도 `OkHttpClient`다. Refresh 요청 자체가 다시 `TokenAuthenticator`에 진입하는 경로가 구조적으로 없다.
- **테스트 근거**: `TokenAuthenticatorResponseCountTest`의 마지막 케이스가 `TokenAuthenticator.AUTH_LOGIN_PATH`/`AUTH_REFRESH_PATH`(운영 상수)가 `AuthInterceptor.AUTH_EXCLUDED_PATHS`(운영 상수)와 값이 일치함을 검증한다. **다만 이 테스트는 상수 값 일치만 확인할 뿐, `authenticate()`가 실제로 이 경로에서 `null`을 반환하는 분기 자체를 실행하지는 않는다**(`TokenStore` 의존성 때문에 `TokenAuthenticator` 인스턴스를 만들 수 없음). 이 분기의 정확성은 코드 읽기로 확인했으며, 자동화 테스트로 커버되지 않은 지점으로 §6/§7에 남긴다.

### M2. 로그인 오류 메시지 분류 — **해결**

- **근거**: `LoginErrorMapper.kt` — `HttpException`이면 에러 바디를 `AuthErrorResponse`로 파싱해 `error` 필드가 `invalid_credentials`/`validation_failed`일 때만 고정된 한국어 문구를 반환하고, 그 외(파싱 실패 포함)는 `GENERIC_ERROR_MESSAGE`. `IOException`은 네트워크 문구, 그 외 모든 예외는 제네릭 문구. **서버 `message` 필드 값을 그대로 신뢰해 노출하는 코드 경로가 없다** — `error` 코드만 보고 매핑한다(Fix Workflow 지시 "서버 message를 무조건 신뢰하지 말고" 그대로 반영).
- **로깅**: `AuthViewModel.kt:79` — `Log.e(TAG, "login failed", throwable)` 단 한 번만 호출된다(1차 리뷰의 중복 `Log.e` 두 곳이 하나로 통합됨). 토큰/비밀번호/Request Body/Authorization 헤더를 인자로 넘기는 코드는 어디에도 없다.
- **테스트 근거**: `LoginErrorMapperTest`(5케이스)가 실제 `LoginErrorMapper.resolve()`(운영 객체)를 `retrofit2.Response.error(...)`로 만든 진짜 `HttpException`, 진짜 `IOException`, 임의의 `IllegalStateException`으로 호출해 검증한다. 마지막 케이스는 `"token save failed"`라는 원본 메시지가 반환 문자열에 **포함되지 않음**을 명시적으로 assert한다 — M2의 핵심 요구("원본 예외 노출 금지")를 직접 증명하는 강한 회귀 테스트다.

### M3. TokenStore 중복 생성 — **해결**

- **근거**: `grep -rn "TokenStore(" app/src/main/java` 결과 실제 생성 지점은 `NetworkModule.kt:23`(`NetworkModule.initialize()` 내부) **단 한 곳**뿐이다. `AuthViewModel.kt:26`은 `NetworkModule.getTokenStore()`로 동일 인스턴스를 재사용하며, `AuthInterceptor`/`TokenAuthenticator`도 `NetworkModule`이 보관한 동일 `tokenStore` 필드를 생성자로 주입받는다(`NetworkModule.kt:41,45`). `SessionManager.restore()`도 이 인스턴스를 파라미터로 받는다(`NetworkModule.kt:24`).
- **초기화 순서 안전성**: `NetworkModule.getTokenStore()`(`:61-67`)는 `check(::tokenStore.isInitialized)`로 미초기화 접근을 명확한 예외 메시지와 함께 즉시 실패시킨다 — `AuthViewModel`은 `PrivateAgentApplication.onCreate()` 이후에만 생성되므로 실제로 이 예외가 발생할 경로는 없음을 확인했다(§4 C2와 동일한 초기화 순서 보장에 의존).

---

## 5. 신규 Critical/Major/Minor/Good

### Critical
없음.

### Major
없음(Android 코드 기준). 단, §9에 기록한 Backend 저장소의 커밋 상태 관련 위험은 Android 코드의 결함이 아니므로 여기 포함하지 않는다.

### Minor

**m-new-1. F2/C2의 일부 분기가 자동화 테스트로 직접 검증되지 않음**
- 파일: `TokenAuthenticator.kt`(`authenticate()` 전체), `data/auth/SessionManager.kt`(`restore()` 전체), `AuthInterceptor.kt`(`intercept()` 전체)
- `TokenStore`가 Android `Context`를 요구하는 구체 클래스이고 프로젝트에 Mock 라이브러리가 없어, 이 세 메서드의 실제 실행 경로(부수효과 포함)는 유닛테스트가 아니라 코드 읽기로만 검증됐다. 순수 로직(상수, `decideRestoredState`/`isPartialTokenPair`, `responseCount`, `LoginErrorMapper.resolve`)은 잘 분리되어 테스트되지만, 이를 실제로 호출/조합하는 통합 지점 자체는 무보호 상태다.
- 시나리오: 향후 이 세 메서드 중 하나를 수정하다가 순수 함수 호출 순서나 조건 결합을 실수로 바꿔도, 현재 테스트 스위트는 이를 잡아내지 못한다.
- 권장 방향: `TokenStore`를 인터페이스로 분리하거나 Mock 라이브러리를 도입해야 하는데, 이는 Fix Workflow가 명시적으로 금지한 "대규모 재설계"에 해당할 수 있어 이번 범위에서는 손대지 않는 것이 맞다. 다음 Feature에서 테스트 전략을 별도로 논의할 것을 권장.
- 커밋 전 수정 필요 여부: 불필요(확인 필요 항목으로 기록, §7 참고).

### Good

- **F1~F5 전부 코드 근거로 해결 확인**(§4 참고).
- **빌드 경고 0건**: `./gradlew :app:compileDebugKotlin --rerun`을 강제 재컴파일했을 때 warning/error 문자열이 전혀 출력되지 않았다(1차 Review 시점에도 경고가 없었고, Fix 이후에도 새 경고가 유입되지 않았다).
- **DTO/Endpoint 무변경**: `AgentApi.kt`, DTO 6개, `TokenStore.kt`가 Fix 전후로 바이트 단위로 동일하다(git diff 없음) — Fix Workflow의 "API Endpoint 변경 금지" 제약을 코드로 준수했다.
- **비인증 화면 무회귀**: `MainScreen`/`PlanScreen`/`ReviewScreen`과 그 ViewModel은 이번 Fix에서 전혀 수정되지 않았다. `MainActivity`에 추가된 것은 세션 관찰용 `LaunchedEffect` 하나뿐이며 기존 `NavHost` 구조는 그대로다.
- **Context/Coroutine 누수 없음**: `SessionManager`는 Context를 전혀 보관하지 않는 순수 `object`다. `collectAsState()`는 Compose 컴포지션 생명주기에 바인딩되어 Activity 종료 시 자동 해제된다. 별도의 리스너 등록/해제 누락 지점을 발견하지 못했다.

---

## 6. 수정으로 인한 회귀 검증

| 항목 | 결과 | 근거 |
|---|---|---|
| 원 요청 최대 1회 재시도 | 유지 | `responseCount(response) >= MAX_REQUEST_COUNT(=2)` 시 즉시 `null` |
| 재시도도 401이면 추가 Refresh 없음 | 유지 | 위와 동일 조건, F2 가드보다 뒤에 있지만 로직 순서상 문제 없음 |
| 동시 401 시 Refresh 1회만 실행 | 유지(코드 근거) | `synchronized(this)`가 단일 `TokenAuthenticator` 싱글턴 인스턴스 전체를 직렬화. **실기 동시성 검증은 미수행**(§8) |
| 대기 요청의 갱신된 토큰 재사용 | 유지, C1으로 실질적으로 정상화 | 공백 버그가 고쳐져 토큰 비교가 정확해짐 |
| Refresh/인증 Client 간 교착·순환 없음 | 유지 | `refreshClient`에 Authenticator 미부착 → 재진입 불가. `.execute()`는 동기 실행으로 별도 디스패처 스레드를 점유하지 않음 |
| Refresh 400/401만 명시적 거절로 분류 | 유지 | `TokenAuthenticator.kt:74-82` |
| 기타 4xx/5xx/네트워크/타임아웃/디코딩 오류는 토큰 유지 | 유지 | 위 조건 외 모든 실패 경로가 `clearTokens()`/`onForcedLogout()` 호출 없이 `null` 반환 |
| 새 토큰 쌍 저장 실패 ≠ 인증 성공 | 유지 | `:96-100` |
| 로그인 중복 제출 방지 | 유지 | `AuthViewModel.kt:35-37` `if (uiState.isLoading) return` |
| 이메일만 trim, 비밀번호 무변형 | 유지 | `:47-48` |
| `/health,/auth/login,/auth/refresh,/api/plan` 인증 제외 정책 | 유지(단, `/api/plan`은 §9 참고) | `AuthInterceptor.AUTH_EXCLUDED_PATHS` 리터럴 불변 |
| 비인증 기능/Main 화면 회귀 없음 | 없음 확인 | §5 Good 참고 |
| Context/Coroutine/Observer 누수 없음 | 없음 확인 | §5 Good 참고 |

새로 발견된 Critical/Major는 없다.

---

## 7. 신규 테스트의 실제 검증 범위

5개 파일, 21개 테스트(신규 20 + 기존 템플릿 1) 전부 실행해 통과를 확인했다(§8). 각 파일이 검증하는 대상을 다음과 같이 판정한다.

| 테스트 파일 | 검증 대상 | 운영 코드 직접 호출 여부 | 커버하지 못하는 지점 |
|---|---|---|---|
| `AuthHeaderTest` | `AuthHeader.BEARER_PREFIX` 공백 포함, 토큰 왕복 무변형 | 예(운영 상수 직접 참조) | 없음 — 목적에 정확히 부합 |
| `AuthInterceptorExcludedPathsTest` | `AuthInterceptor.AUTH_EXCLUDED_PATHS` 값 | 예(운영 상수) | `intercept()`가 이 값을 실제로 사용해 헤더를 스킵하는 동작 자체는 미검증 |
| `TokenAuthenticatorResponseCountTest` | `responseCount()` 재시도 카운트, `AUTH_LOGIN_PATH`/`AUTH_REFRESH_PATH` 상수 일치 | 예(운영 companion 함수/상수, 진짜 OkHttp `Response` 체인 사용) | `authenticate()`의 F2 조기 반환 분기, 토큰 비교/Refresh 호출/저장 분기 자체는 미검증(TokenStore Context 의존) |
| `SessionManagerRestoreLogicTest` | `decideRestoredState`/`isPartialTokenPair` 4가지 토큰 조합 | 예(운영 함수) | `restore()`의 실제 부수효과(`clearTokens()` 호출, `_authState.value` 대입)는 미검증 |
| `LoginErrorMapperTest` | `LoginErrorMapper.resolve()` 전체 분기 + 원본 메시지 비노출 | 예(운영 객체, 진짜 `HttpException`/`IOException`) | 없음 — M2 요구사항을 가장 직접적으로 증명 |

**결론**: 5개 테스트 모두 내부 복제 로직이 아니라 실제 운영 코드(상수, companion 함수, 독립 object)를 대상으로 하며, 우연히 통과할 만큼 단순하지 않다(경계값·복수 조합·"금지된 문자열 미포함" 등 실질적 assertion 포함). 다만 `TokenStore`의 Android 의존성 때문에 `authenticate()`/`intercept()`/`restore()` 세 통합 메서드 자체는 어떤 테스트로도 직접 실행되지 않는다 — 이는 테스트 개수와 별개로 반드시 기록해야 할 커버리지 공백이다(§5 m-new-1).

---

## 8. 빌드 및 테스트 실행 결과

```
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest
→ BUILD SUCCESSFUL

JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug
→ BUILD SUCCESSFUL

JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:compileDebugKotlin --rerun
→ BUILD SUCCESSFUL, warning/error 0건
```

테스트 결과 XML(`app/build/test-results/testDebugUnitTest/*.xml`) 실측:

| Suite | tests | failures | errors |
|---|---|---|---|
| `AuthHeaderTest` | 2 | 0 | 0 |
| `AuthInterceptorExcludedPathsTest` | 3 | 0 | 0 |
| `TokenAuthenticatorResponseCountTest` | 4 | 0 | 0 |
| `SessionManagerRestoreLogicTest` | 6 | 0 | 0 |
| `LoginErrorMapperTest` | 5 | 0 | 0 |
| `ExampleUnitTest`(기존) | 1 | 0 | 0 |
| **합계** | **21** | **0** | **0** |

모두 통과, 실패/에러/스킵 없음.

---

## 9. 사용자 실기기 검증과 Claude 직접 검증의 구분

이번 대화에서 사용자가 실제로 보고한 내용은 다음 두 마디뿐이다(추가 확대 해석하지 않는다).

1. "실행해 봤는데 왜 로그인을 안해도 메인액티비티로 바로 넘어가게 되지?" — Fix 이전에 로그인해 저장된 토큰이 남은 기기/에뮬레이터에서 앱을 재실행하자 로그인 화면 없이 바로 `MainActivity`로 이동함을 관찰.
2. "삭제후 재실행 하니까 잘 되네" — 앱 데이터를 삭제(=토큰 삭제)하고 재설치/재실행하니 정상적으로 로그인 화면부터 시작됨을 확인.

이를 워크플로 §9의 4개 최소 확인 대상과 대조하면:

```text
사용자 실기기 검증: 완료 보고됨
  - 앱 완전 종료 후 재실행 시 Main 화면 복원: 완료 보고됨(1번 관찰이 곧 이 시나리오의 실측 증거 — 저장된 토큰이 있는 상태에서 재실행하면 Main으로 감을 사용자가 직접 목격함)
  - 토큰이 없는 상태에서 Login 화면부터 시작: 완료 보고됨(2번)
  - 정상 로그인 및 Main 화면 이동(이메일/비밀번호를 직접 입력해 로그인 버튼을 눌러 성공한 경우): 명시적으로 보고되지 않음 — "잘 되네"가 로그인 폼 진입까지만 의미하는지 실제 로그인 성공까지 포함하는지 대화 내용만으로는 단정할 수 없음
  - 만료된 Access Token → 401 → Refresh → 원 요청 재시도 성공: 보고되지 않음
  - 무효한 Refresh Token 또는 Refresh 400/401 → 토큰 삭제 및 Login 화면 전환: 보고되지 않음

Claude 독립 런타임 검증: 수행하지 않음
  - 로컬 Android SDK의 AVD(Pixel_10_test)를 headless로 부팅 시도했으나(Fix 세션에서 약 15분 대기) sys.boot_completed에 도달하지 못해 중단했다. 이번 재리뷰 세션에서는 동일한 제약이 반복될 것으로 판단해 재시도하지 않았다.
  - 따라서 "401 → Refresh → 재시도 성공"과 "Refresh 거절 → 강제 로그아웃"은 Claude가 직접 실기로 확인한 사실이 아니며, 코드 읽기(§4, §6)로만 정합성을 확인한 상태다.

정적 코드/유닛테스트/빌드 검증: Claude가 실제 수행
  - §4, §6, §7, §8에 기록한 내용 전부.
```

**충돌 여부**: 사용자 보고와 코드 근거 사이에 충돌은 없다. 사용자가 관찰한 두 현상(토큰 있으면 Main 직행, 토큰 없으면 Login 표시)은 `SessionManager.restore()`의 설계·구현과 정확히 일치한다. 다만 이는 "세션 복원" 경로만 실증한 것이고, "401 발생 → Refresh → 재시도 성공"이라는 이번 Fix의 가장 핵심적인 회귀(C1) 시나리오는 사용자 보고에도, Claude 직접 검증에도 포함되어 있지 않다 — 이 부분은 **미검증 상태로 명확히 남긴다.**

---

## 10. 문서·Backend·iOS와 실제 구현의 차이

### 10.1 기존에 알려진 차이 (재확인, Android 결함으로 재판정하지 않음)
- 설계서/`backend-api-spec.xlsx`는 `/api/auth/login`, `/api/auth/refresh`로 기록하지만, Android는 `/auth/login`, `/auth/refresh`를 사용한다. 워크플로 지시대로 이번에도 Android 코드를 문서에 맞춰 되돌리지 않았다.

### 10.2 이번 재리뷰에서 새로 발견한 사실 — Backend 저장소가 이 마운트 경로에 대해 **커밋되지 않은 상태**임

1차 Review는 Backend 파일 내용만 확인하고 `git status`는 확인하지 않았다. 이번 재리뷰에서 Backend 저장소의 `git status`/`git diff`를 처음으로 확인한 결과:

```
private-agent-backend $ git status --short
 M package.json
 M src/index.js
 M src/routes/api.js
?? scripts/seed-dev-test-user.js

private-agent-backend $ git diff -- src/index.js
- app.use("/api/auth", authRouter);   // 마지막 커밋(82fe2d4)의 실제 내용
+ app.use("/auth", authRouter);        // 현재 작업 트리(커밋 안 됨)

private-agent-backend $ git diff -- src/routes/api.js
- router.post("/plan", authMiddleware, apiController.generatePlan);   // 마지막 커밋에는 존재
  (현재 작업 트리에는 이 줄이 삭제되어 없음)
```

즉:
- Android가 의존하는 "`/auth/login`, `/auth/refresh`가 현재 Backend 실제 구현"이라는 전제는 **Backend 저장소의 마지막 커밋 기준으로는 사실이 아니다.** 마지막 커밋에는 여전히 `/api/auth/*`로 마운트되어 있다. 현재 로컬에서 실행 중인 개발 서버(`curl http://localhost:3000/health` 응답 확인함)가 `/auth/*`로 동작하는 이유는, 누군가(사용자로 추정)가 로컬 작업 트리에서 마운트 경로를 수정했지만 아직 커밋하지 않았기 때문이다.
- 1차 리뷰에서 "`/api/plan`이 Backend에 등록돼 있지 않다"고 기록한 것도 같은 이유로 **마지막 커밋 기준으로는 사실이 아니다** — 커밋된 버전에는 `authMiddleware`로 보호된 `/plan` 라우트가 존재한다. 현재 작업 트리에서만 이 줄이 삭제되어 없다.
- **위험**: 이 Backend 변경이 커밋되지 않은 채 되돌려지거나(예: `git checkout`, 다른 브랜치로 전환, 신규 clone/CI/배포가 마지막 커밋 기준으로 이루어지는 경우), Android의 `/auth/login`·`/auth/refresh` 호출은 즉시 404가 되어 로그인 자체가 불가능해진다. 반대로 `/plan`이 다시 보호되면, Android의 `AUTH_EXCLUDED_PATHS`가 `/api/plan`을 계속 인증 제외로 취급하고 있어(§6) 초기 요청에는 토큰이 안 붙지만, `TokenAuthenticator`가 401을 받으면 현재 저장된 Access Token으로 즉시 재시도해 대부분 정상 동작하되 불필요한 왕복이 한 번 더 발생한다(치명적이지는 않음).
- **판정**: 이는 Android 코드의 결함이 아니다(워크플로 §10 지시대로 Android를 오래된 문서에 맞춰 되돌리지 않는다). 다만 "Android 인증 Feature가 실제로 정상 동작하는 것처럼 보이는 근거"가 **Backend 저장소의 커밋되지 않은 로컬 상태**에 의존하고 있다는 사실은 Authentication Feature 전체의 완료 판정에 영향을 주는 중요한 위험이므로 최종 판정(§11)과 남은 위험에 반영한다.
- **권장**: Backend 담당자(또는 다음 세션)가 `src/index.js`의 `/auth` 마운트 변경과 `src/routes/api.js`의 `/plan` 라우트 삭제를 의도한 변경인지 확인하고, 의도한 것이라면 커밋해 둘 것을 권장한다. 이번 재리뷰에서는 Backend 저장소를 수정하지 않았다(작업 제한 준수).

### 10.3 iOS 대비 차이
1차 Review 이후 iOS 저장소에 새로운 변경은 없다(`authentication-ios-review.md`/`authentication-ios-rereview.md` 내용과 이번 확인이 일치). iOS 쪽도 인증 관련 파일 전체가 여전히 커밋되지 않은 상태이며, 이는 기존에 이미 알려진 사실이다.

---

## 11. 남은 위험과 미검증 항목

- **§9에서 지적한 4개 실기 시나리오 중 2개(정상 로그인 성공, 401→Refresh→재시도 성공)와 강제 로그아웃(Refresh 400/401) 시나리오는 Claude도 사용자도 실기로 확인하지 못했다.** 코드 근거(§4, §6)와 `AuthHeaderTest`가 C1의 핵심 원인을 직접 겨냥한 회귀 테스트라는 점은 강한 정황 증거이지만, 실제 기기에서의 종단 간(end-to-end) 동작 확인을 대체하지 않는다.
- **§10.2의 Backend 커밋 상태 위험** — Authentication Feature 전체의 실질적 동작 가능 여부가 Backend 저장소의 미커밋 상태에 달려 있다.
- **m-new-1(§5)** — `authenticate()`/`intercept()`/`restore()` 통합 메서드 자체가 자동화 테스트로 보호되지 않는다.
- 1차 Review에서 확인 필요로 남겼던 항목 중 이번 Fix 범위에 포함되지 않은 것들(예: `androidx.security:security-crypto` alpha 버전 사용, `AuthRepository`가 `refresh()`를 감싸지 않는 구조)은 이번 Fix Workflow의 명시적 범위 밖이라 재확인만 하고 별도 이슈로 재등록하지 않는다.

---

## 12. 최종 판정

- **Critical 0건인가**: 예.
- **Major 0건인가**: 예(Android 코드 기준).
- **현재 상태로 커밋 가능한가**: **예.** Android 코드 자체는 1차 Review의 C1/C2/M1/M2/M3를 모두 코드·테스트 근거로 해결했고, 빌드·유닛테스트가 전부 통과하며, 새로운 Critical/Major가 발견되지 않았다.
- **추가 수정 또는 재리뷰가 필요한가**: Android 코드의 추가 수정은 불필요하다. 다만 다음 두 가지가 해소되기 전까지는 "실사용 준비 완료"로 단정하지 말 것을 권장한다 — (1) §9에서 미검증으로 남긴 로그인 성공/401·Refresh·재시도/강제 로그아웃 시나리오의 실기 검증, (2) §10.2의 Backend 라우트 변경 커밋 여부 확인. 이 두 가지가 확인되면 별도 재리뷰 없이 종료해도 무방하다.
- **Authentication Feature가 완료됐는가**: Android 설계서 기준 범위(로그아웃 UI/`auth/me` 제외)의 코드 구현은 완료됐다. 다만 위 두 잔여 위험이 있어 "완전히 종료"보다는 "커밋 가능한 완료" 상태로 평가한다.
- **다음 Android 기능으로 진행 가능한가**: **예.** 이번 Authentication Feature가 다음 기능의 기반(인증 헤더, 세션 상태)을 안정적으로 제공하므로 진행해도 무방하나, 위 미검증 시나리오는 병행하거나 조속히 실기로 확인할 것을 권장한다.

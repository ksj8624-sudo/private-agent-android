# Android Authentication 코드 리뷰 결과

## 1. 리뷰 일시와 범위

- 리뷰 일시: 2026-08-27
- 리뷰 대상: `/Users/kimseongjin/Desktop/workspace/private-agent`의 현재 미커밋 변경사항(로그인 + JWT Refresh 인증 구현)
- 방식: 코드 수정 없이 정적 코드 리딩 + 빌드/유닛테스트 실행. 실기기/에뮬레이터에서의 런타임 실행은 수행하지 않음(§8 참고).
- Git commit/push는 수행하지 않았음.

---

## 2. 참고한 문서와 실제 코드

### 문서
- `/Users/kimseongjin/Desktop/workspace/ai-agent-lab/docs/prompts/tasks/review.md` (공통 Review 규칙)
- `/Users/kimseongjin/Desktop/workspace/private-agent/docs/designs/android-login-auth.md` (Android 인증 설계서, 전체)
- `/Users/kimseongjin/Desktop/workspace/private-agent/docs/architecture/android-project-overview.md` (Android Architecture)
- `/Users/kimseongjin/Desktop/workspace/private-agent-backend/docs/backend-api-spec.xlsx` (API Contract, `sharedStrings.xml` 직접 파싱해 인증 관련 문자열 전량 확인)
- `/Users/kimseongjin/Desktop/workspace/ios/PrivateAgent/docs/reviews/authentication-ios-review.md`
- `/Users/kimseongjin/Desktop/workspace/ios/PrivateAgent/docs/reviews/authentication-ios-rereview.md`

### 실제 코드 (Android, 변경분 전체 직접 읽음)
- `PrivateAgentApplication.kt`, `AndroidManifest.xml`
- `data/local/TokenStore.kt`
- `data/network/AuthInterceptor.kt`, `data/network/TokenAuthenticator.kt`, `data/network/NetworkModule.kt`
- `data/remote/AgentApi.kt`, `data/remote/dto/{LoginRequest,LoginResponse,RefreshRequest,RefreshResponse,AuthUser,AuthErrorResponse}.kt`
- `data/repository/AuthRepository.kt`
- `ui/viewmodel/AuthViewModel.kt`
- `ui/screen/LoginActivity.kt`, `ui/screen/MainActivity.kt`(변경 없음, 인증 연동 여부 확인 목적으로 전체 읽음)
- `app/build.gradle.kts`

### 실제 코드 (Backend, 실제 구현 확인용)
- `src/index.js`, `src/routes/auth.js`, `src/routes/api.js`, `src/controllers/authController.js`, `src/services/authService.js`, `src/middleware/authMiddleware.js`, `src/repositories/userRepository.js`, `src/config/env.js`

iOS 자료는 요구된 대로 정책/사용자 동작 참고용으로만 사용했으며, `docs/reviews/*.md` 두 문서에 인용된 Swift 코드 근거(파일/라인)를 그대로 신뢰 가능한 근거로 채택했다(Swift 소스 자체를 재오픈하지는 않았음 — 리뷰 문서가 이미 라인 단위 근거를 포함하고 있어 중복 확인 생략).

---

## 3. git 변경 범위

`git status` / `git diff` / `git diff --cached` 확인 결과, staged와 unstaged를 합쳐 다음이 최종 작업 트리 상태다(마지막 커밋 `9203df5`).

**신규 파일**
- `PrivateAgentApplication.kt`, `data/local/TokenStore.kt`, `data/network/AuthInterceptor.kt`, `data/network/TokenAuthenticator.kt`
- `data/remote/dto/{AuthErrorResponse,AuthUser,LoginRequest,LoginResponse,RefreshRequest,RefreshResponse}.kt`
- `data/repository/AuthRepository.kt`, `ui/viewmodel/AuthViewModel.kt`

**수정 파일**
- `app/build.gradle.kts`(`androidx.security:security-crypto:1.1.0-alpha06` 추가)
- `AndroidManifest.xml`(`android:name=".PrivateAgentApplication"` 등록)
- `data/config/ApiConfig.kt`(Base URL 로컬 IP 변경)
- `data/network/NetworkModule.kt`(인증 클라이언트/Refresh 전용 클라이언트 분리)
- `data/remote/AgentApi.kt`(login/refresh 엔드포인트 추가)
- `ui/screen/LoginActivity.kt`(AuthViewModel 연동)

**auth Feature와 무관한 변경**(참고용 기록, 이번 리뷰 대상 아님)
- `.gitignore`, `.idea/vcs.xml` — IDE/VCS 설정 정리. Android 인증 로직과 무관.

`MainActivity.kt`는 이번 diff에 전혀 포함되지 않았다 — 인증 상태 관련 코드가 추가되지 않았다는 뜻이며, 이는 4.3절/6절의 Critical 이슈와 직결된다.

---

## 4. Critical

### C1. `TokenAuthenticator`의 Bearer 접두사 공백 누락으로 Refresh/재시도 메커니즘이 사실상 전혀 동작하지 않음

- **파일과 위치**: `data/network/TokenAuthenticator.kt:104-105`(`BEARER_PREFIX = "Bearer"`, 공백 없음), 사용처는 `:22-24`(`removePrefix`), `:34-38`, `:82-87`
- **문제 코드**:
  ```kotlin
  companion object {
      private const val AUTHORIZATION = "Authorization"
      private const val BEARER_PREFIX = "Bearer"   // 끝에 공백 없음
      ...
  }

  val requestAccessToken = response.request
      .header(AUTHORIZATION)
      ?.removePrefix(BEARER_PREFIX)   // "Bearer abc" -> " abc" (앞 공백이 남음)

  val currentAccessToken = tokenStore.getAccessToken()   // "abc" (공백 없음)

  if (!currentAccessToken.isNullOrEmpty() &&
      currentAccessToken != requestAccessToken) {   // "abc" != " abc" → 항상 true
      return@synchronized response.request.newBuilder()
          .header(AUTHORIZATION, "$BEARER_PREFIX$currentAccessToken")  // "Bearerabc" (공백 없음)
          .build()
  }
  ```
- **발생 가능한 실제 시나리오**: `AuthInterceptor`가 원 요청에 붙이는 헤더는 `"Bearer $accessToken"`(공백 포함, `AuthInterceptor.kt:27`)이다. Access Token이 만료돼 401을 받으면 `TokenAuthenticator.authenticate()`가 호출되는데, `removePrefix("Bearer")`는 문자열 `"Bearer"`만 제거하므로 결과에 선행 공백이 그대로 남는다(`" abc"`). 반면 `tokenStore.getAccessToken()`으로 읽은 값에는 공백이 없다(`"abc"`). 따라서 **아직 아무도 Refresh를 수행하지 않은 최초 401 상황에서도 두 값은 항상 다르다고 판정되어**, 코드는 "다른 스레드가 이미 갱신을 완료했다"고 오판하고 **실제 Refresh 네트워크 호출을 한 번도 실행하지 않은 채** 같은(만료된) 토큰으로 즉시 재시도 요청을 만든다. 그마저도 재시도 헤더 값이 `"Bearerabc"`(공백 없음)로 조립되어, 백엔드 `authMiddleware.js:8`의 `header.startsWith("Bearer ")`(공백 포함) 검사를 통과하지 못해 다시 401이 발생한다. 이 시점에 `responseCount(response) >= 2`가 되어 Authenticator는 포기하고 원래의 401을 그대로 호출자에게 반환한다.
  - 결과적으로 **정상적인 로그인 상태에서 Access Token이 만료되는 모든 경우**(워크플로 필수 런타임 시나리오 "만료된 Access Token으로 요청 후 Refresh 성공")에 실제로는 Refresh가 한 번도 호출되지 않고, 사용자에게는 그냥 요청 실패(401)만 반환된다.
  - 동시 401 상황에서도 동일 로직이 각 스레드에서 반복되므로, "동시 401 시 단일 Refresh 실행"이라는 요구사항 자체를 검증할 수 없다(Refresh 호출이 아예 발생하지 않으므로 "여러 번 호출되는지"를 따질 대상이 없음).
- **설계 문서와의 차이**: `docs/designs/android-login-auth.md` §10.3("Authorization 헤더만 새 토큰으로 교체"), §11("토큰 세대 비교로 동시 Refresh를 한 번만 실행")이 요구하는 동작이 이 버그로 인해 원천적으로 성립하지 않는다.
- **권장 수정 방향**: `BEARER_PREFIX`를 `"Bearer "`(공백 포함)로 바꾸거나, 헤더 값에서 토큰만 안전하게 추출하도록 `substringAfter("Bearer ").trim()` 등으로 수정한다. 헤더 조립 시에도 `"Bearer $token"`처럼 공백이 포함된 템플릿을 일관되게 사용해야 한다.
- **커밋 전 수정 필요 여부**: **필수.** 이 버그가 남아있는 한 이번 Feature의 핵심 기능(Access Token 만료 시 자동 갱신)이 사실상 존재하지 않는 것과 같다.

### C2. 앱 시작 시 인증 상태 복원, 그리고 Refresh 거절 시 화면 강제 전환 로직이 전혀 구현되어 있지 않음

- **파일과 위치**: `ui/screen/MainActivity.kt`(전체, 변경 없음 — 인증 관련 코드 0건), `ui/screen/LoginActivity.kt`(전체 — 저장된 토큰 유무를 확인하는 코드 없음), 앱 전체에 `SessionManager`/`StateFlow<AuthState>`(앱 전역 인증 상태) 유형의 컴포넌트가 존재하지 않음(`grep -rniE "sessionmanager" app/src/main/java` 결과 0건)
- **문제 코드 근거**: `LoginActivity.onCreate()`는 `AuthViewModel`의 로컬 화면 상태(`authState.isLoginSuccess`)만 관찰할 뿐, 앱 실행 시점에 `TokenStore`에 저장된 토큰 유무를 확인하는 코드가 없다. `AuthViewModel`의 `AuthState` data class는 로그인 폼 하나의 화면 상태일 뿐 앱 전역 인증 상태가 아니며, `MainActivity`는 인증/토큰 관련 코드를 전혀 참조하지 않는다.
- **발생 가능한 실제 시나리오**:
  1. (워크플로 필수 런타임 시나리오) 정상 로그인 후 앱을 완전히 종료했다가 다시 실행하면, 유효한 토큰 쌍이 `TokenStore`에 남아있어도 `LoginActivity`가 무조건 로그인 폼부터 다시 보여준다 — "저장된 토큰이 있으면 Main 화면으로 진입"이라는 요구사항(§5.7)이 지켜지지 않는다.
  2. `MainActivity`에서 Plan/Review 기능을 쓰는 도중 백그라운드에서 Refresh가 명시적으로 거절(400/401)되어 `TokenAuthenticator`가 `tokenStore.clearTokens()`를 호출해도, 이 변화를 관찰하는 코드가 앱 어디에도 없다. 사용자는 토큰이 사라진 `MainActivity`에 계속 머무르고, 이후의 모든 인증 필요 요청이 원인 불명으로 계속 실패하며, 로그인 화면으로 강제 이동되지 않는다.
- **설계 문서와의 차이**: `docs/designs/android-login-auth.md` §12(SessionManager, `AuthState.Unknown/LoggedOut/LoggedIn`), §13.2~13.6(앱 시작 시 복원 순서, Refresh 거절 시 `MainActivity.finish()` 후 로그인 화면 복귀)이 명시한 내용이 전혀 구현되지 않았다. 워크플로 §4의 필수 구현 범위("앱 재실행 시 저장된 인증 상태 확인 및 초기 화면 결정")이자 최종 판정 질문("앱 인증 상태 복원까지 구현됐는가")에 직접 해당하는 항목으로, 답은 **"아니오"**다.
- **권장 수정 방향**: 설계서 §12/§13에 따라 앱 전역 인증 상태(`StateFlow` 등)를 갖는 컴포넌트를 신규 도입하고, `LoginActivity`는 시작 시 `TokenStore`를 조회해 즉시 분기하도록, `MainActivity`는 강제 로그아웃 상태를 관찰해 `LoginActivity`로 이동 + `finish()`하도록 구현해야 한다.
- **커밋 전 수정 필요 여부**: **필수.** 이번 Feature의 명시적 필수 범위(§4 "이번 구현 범위") 중 하나가 완전히 누락된 상태다.

---

## 5. Major

### M1. `TokenAuthenticator`가 로그인/리프레시 요청 자체의 401에도 반응함(인증 API 재진입 방지 누락)

- **파일과 위치**: `data/network/TokenAuthenticator.kt`(전체) — 실패한 요청이 `/auth/login` 또는 `/auth/refresh`인지 확인하는 로직이 없음. `ui/viewmodel/AuthViewModel.kt:27`에서 로그인 호출에 `NetworkModule.agentApi`(=`AuthInterceptor`+`TokenAuthenticator`가 붙은 `authenticatedClient`)를 그대로 사용.
- **발생 가능한 실제 시나리오**: 이미 유효한 토큰이 저장된 상태(예: 로그인된 채로 다시 로그인 화면에 진입해 다른 계정으로 로그인 시도)에서 잘못된 비밀번호로 로그인하면 백엔드가 401(`invalid_credentials`)을 반환한다. 이 로그인 응답도 `TokenAuthenticator.authenticate()`로 들어가며, `currentAccessToken`(기존 유효 토큰)과 `requestAccessToken`(로그인 요청엔 Authorization 헤더가 없으므로 `null`)이 다르다고 판정되어, 실제 로그인 실패와 무관하게 로그인 POST 요청을 불필요하게 한 번 더 재시도한다. 최종 사용자 화면 결과는 우연히 동일(로그인 실패)하지만, 설계서 §10.5가 명시한 "Refresh/로그인 요청은 Authenticator 대상에서 제외"가 구현돼 있지 않아 불필요한 네트워크 호출과 예측 불가능한 부작용 소지가 남는다.
- **권장 수정 방향**: `authenticate()` 최상단에서 `response.request.url.encodedPath`가 `/auth/login` 또는 `/auth/refresh`이면 즉시 `null`을 반환하도록 가드를 추가한다.
- **커밋 전 수정 필요 여부**: 권장. C1 수정과 함께 처리하는 것이 효율적이다.

### M2. 로그인 실패 메시지가 분류 없이 원본 예외 메시지를 그대로 노출함 — `AuthErrorResponse`는 정의만 되고 실제로 사용되지 않음

- **파일과 위치**: `ui/viewmodel/AuthViewModel.kt:98-105`(`onFailure` 분기), `data/remote/dto/AuthErrorResponse.kt`(정의부 외 참조 0건 확인)
- **문제 코드**:
  ```kotlin
  }.onFailure { throwable ->
      ...
      authState = authState.copy(
          isLoading = false,
          result = "",
          errorMessage = throwable.message ?: "로그인에 실패했습니다.",
          isLoginSuccess = false
      )
  }
  ```
- **발생 가능한 실제 시나리오**: `repository.login()`은 `agentApi.login()`(suspend fun)을 그대로 호출할 뿐이며, HTTP 오류 시 Retrofit이 던지는 `HttpException`의 `message`는 백엔드가 내려주는 한국어 안내문(`AuthErrorResponse.message`, 예: "이메일 또는 비밀번호가 올바르지 않습니다.")이 아니라 `"HTTP 401 "` 같은 HTTP 상태줄 문자열이다. 네트워크 오류(`UnknownHostException`, `SocketTimeoutException` 등) 시에는 원본 영어 예외 메시지가 그대로 `AlertDialog`에 표시된다(예: 오프라인 상태에서 로그인 시도 시 기술적인 영문 오류 문구 노출).
- **설계 문서와의 차이**: `docs/designs/android-login-auth.md` §14.3(`LoginResult.Rejected`/`SystemFailure` 분리), §14.4("서버 원문 그대로 노출 금지")를 구현하지 않았다. 워크플로 §5.1 "서버 내부 오류나 민감한 오류 메시지를 사용자에게 그대로 노출하지 않는가" 항목에 위배된다. `AuthErrorResponse` DTO는 실제로 파싱하는 코드가 없어 죽은 코드로 남아 있다.
- **권장 수정 방향**: `HttpException`의 응답 바디를 `AuthErrorResponse`로 역직렬화해 `message` 필드를 사용자에게 노출하고, `IOException` 등 시스템 오류에는 고정된 일반 문구("네트워크 상태를 확인해 주세요" 등)를 사용하도록 분류 로직을 추가한다.
- **커밋 전 수정 필요 여부**: 권장. 토큰/비밀번호 등 진짜 민감정보가 새는 것은 아니므로 Critical은 아니지만, 사용자 경험과 설계 준수 관점에서 커밋 전 반영을 권장한다.

### M3. `TokenStore` 인스턴스가 `NetworkModule`과 `AuthViewModel`에서 각각 별도로 생성됨 — 설계된 공유 지점(`getTokenStore()`)이 사용되지 않는 죽은 코드로 남음

- **파일과 위치**: `ui/viewmodel/AuthViewModel.kt:28`(`val tokenStore = TokenStore(application.applicationContext)`), `data/network/NetworkModule.kt:59-65`(`getTokenStore()` — 호출부 0건, `grep -rn "getTokenStore" app/src/main/java` 확인)
- **문제 상황**: `NetworkModule.initialize()`가 `Application.onCreate()`에서 만든 `TokenStore` 싱글턴은 `AuthInterceptor`/`TokenAuthenticator`에서만 쓰이고, 로그인 흐름(`AuthViewModel`)은 이를 재사용하지 않고 동일한 파일(`auth_tokens`)을 대상으로 `MasterKey`/`EncryptedSharedPreferences`를 처음부터 다시 생성한다. Android의 `Context.getSharedPreferences()` 캐싱 덕분에 두 인스턴스가 결과적으로 같은 파일을 공유해 즉각적인 데이터 불일치까지는 발생하지 않는 것으로 판단되나(동일 applicationContext + 동일 파일명), Keystore/암호화 초기화가 불필요하게 두 번 일어나고, "TokenStore 인스턴스가 불필요하게 중복 생성되지 않는가"(워크플로 §5.2)를 명백히 위반한다. 이를 막기 위해 설계서가 명시한 `NetworkModule.getTokenStore()` 공유 지점은 정의만 되고 아무도 호출하지 않는 죽은 코드다.
- **권장 수정 방향**: `AuthViewModel`(또는 `AuthViewModel`을 생성하는 Factory)이 `NetworkModule.getTokenStore()`를 통해 동일 인스턴스를 주입받도록 수정한다.
- **커밋 전 수정 필요 여부**: 권장(구조적 위반이나 즉각적인 데이터 손상은 확인되지 않음).

---

## 6. Minor

### m1. 로그인 입력 검증이 설계보다 약함
- `ui/viewmodel/AuthViewModel.kt:40` — `email.isEmpty() || password.isEmpty()`만 검사해 공백 문자열(`" "`)은 통과시킨다. `android.util.Patterns.EMAIL_ADDRESS` 기반 이메일 형식 검증(설계 §13.7 권장)도 구현되어 있지 않다. 백엔드가 `isBlank`/이메일 정규식으로 다시 검증하므로 보안·정합성 문제는 없으나, 불필요한 네트워크 요청과 사용자에게 즉시 피드백을 주지 못하는 손해가 있다.

### m2. `Authorization` 헤더 이름 표기가 파일마다 다름
- `AuthInterceptor.kt:35` — `"AUTHORIZATION"`(전체 대문자), `TokenAuthenticator.kt:104` — `"Authorization"`(일반 표기). OkHttp의 헤더 조회가 대소문자 무관이라 기능상 문제는 없으나 컨벤션 일관성이 떨어진다.

### m3. `AuthViewModel.login()` 실패 처리에서 `Log.e`가 사실상 같은 정보를 두 번 로깅
- `ui/viewmodel/AuthViewModel.kt:87-104` — 상세 메시지를 담은 `Log.e("AuthViewModel", ...)` 호출 직후 `Log.e("error", throwable.message ?: ...)`를 한 번 더 호출한다. 토큰 값은 포함하지 않아 보안 문제는 아니지만 중복 코드다.

### m4. `TokenStore` 읽기(`getAccessToken`/`getRefreshToken`) 도중 발생 가능한 예외에 대한 명시적 처리 없음
- `TokenAuthenticator.kt:26,41` — `tokenStore.getAccessToken()`/`getRefreshToken()` 호출에 try/catch가 없다. `EncryptedSharedPreferences` 읽기가 이론적으로 `GeneralSecurityException` 등을 던질 경우(설계 §14.1의 "TokenStore 읽기 실패 → 토큰 유지" 버킷 B) 이 예외가 `authenticate()` 밖으로 그대로 전파된다. 발생 가능성은 낮지만 워크플로 §5.6에 명시적으로 요구된 항목이라 기록한다.

### m5. `androidx.security:security-crypto:1.1.0-alpha06`(alpha 버전) 사용
- `app/build.gradle.kts` — 토큰처럼 민감한 데이터를 다루는 저장소에 alpha 등급 라이브러리를 채택했다. 현재 AndroidX Security Crypto 생태계 자체가 안정 버전을 오래 내놓지 못하고 있는 상황(설계서 §22에도 기록된 재검토 대상)이라 이번 구현만의 문제는 아니지만, 배포 전 재검토가 필요한 확인 필요 항목으로 남긴다.

### m6. `LoginActivity.kt` 하단에 주석 처리된 `@Preview` 코드가 삭제되지 않고 남아 있음
- `ui/screen/LoginActivity.kt:146-157` — 새 `LoginScreen` 시그니처와 맞지 않아 통째로 주석 처리된 채 방치되어 있다. 빌드에는 영향 없으나 죽은 코드다.

### m7. `AuthRepository`가 `login()`만 감싸고 `refresh()`는 감싸지 않음
- `data/repository/AuthRepository.kt` — 설계서 §7.4는 로그인/리프레시 모두 `AuthRepository`를 거쳐 에러 분류 로직을 공유하도록 권장했으나, 실제 리프레시 호출은 `TokenAuthenticator`에서 `refreshApi`를 직접 사용한다. 책임 분리 자체(리프레시 로직이 ViewModel에 남지 않음, §5.9)는 지켜졌으므로 기능적 결함은 아니며, 향후 에러 분류 로직을 공유하려면 리팩터링이 필요하다는 참고 사항이다.

---

## 7. Good (코드 근거로 정상 확인된 항목)

- **중복 로그인 요청 방지**: `AuthViewModel.login()` 최상단의 `if (authState.isLoading) return` 가드(`AuthViewModel.kt:37-39`)와 `LoginScreen`의 `enabled = !isLoading`(`LoginActivity.kt:135`)이 이중으로 막고 있다. iOS `LoginViewModel.login()`의 `guard !isLoading else { return }`과 동일한 설계다.
- **로그인 성공 조건**: `AuthViewModel.kt:63-84` — `tokenStore.saveTokens(...)`가 `true`를 반환할 때만 `isLoginSuccess = true`로 전환되고, 저장 실패 시 `clearTokens()` 후 예외를 던져 로그인 상태로 전환되지 않는다(설계 정책 1과 일치).
- **토큰 쌍 저장의 원자성**: `TokenStore.kt:24-27` — `edit().putString(ACCESS,...).putString(REFRESH,...).commit()`을 단일 트랜잭션으로 묶어, 한쪽만 저장되고 다른 쪽이 누락되는 부분 쓰기 상태가 발생하지 않는다(설계 §8.2 원칙과 일치).
- **로그인 화면 Back Stack 정리**: `LoginActivity.moveToMain()`(`:71-75`)이 기존과 동일하게 `startActivity + finish()`를 유지해, 로그인 성공 후 기기 뒤로가기로 로그인 화면에 돌아갈 수 없다.
- **Application/NetworkModule 초기화 순서**: `PrivateAgentApplication.onCreate()` → `NetworkModule.initialize(applicationContext)`가 Android 프로세스 시작 시 항상 모든 Activity보다 먼저 실행되고, `NetworkModule.agentApi`/`tokenStore`의 최초 접근은 `AuthViewModel` 생성 시점(Activity 이후)에만 발생해 `UninitializedPropertyAccessException` 위험이 없다. `initialize()`는 `::tokenStore.isInitialized` 가드로 중복 초기화도 방지한다.
- **Refresh 전용 OkHttpClient 분리**: `NetworkModule.kt:30-34`의 `refreshClient`/`refreshApi`는 `AuthInterceptor`/`TokenAuthenticator`가 붙지 않은 별도 클라이언트로, Refresh 요청 자체가 다시 `TokenAuthenticator`에 진입하는 순환이 없다(워크플로 §5.5 요구사항 충족).
- **원 요청 재시도 횟수 제한**: `TokenAuthenticator.responseCount()`(`:91-101`)가 `priorResponse` 체인을 순회해 재시도 횟수를 정확히 계산하고, `MAX_REQUEST_COUNT = 2`로 무한 재시도를 차단한다(설계 §10.4와 동일한 관용구).
- **Refresh 실패 분류 로직 자체는 iOS 버킷 A/B 정책과 일치**: `TokenAuthenticator.kt:43-80` — Refresh Token 없음/400/401/새 토큰 저장 실패는 `clearTokens()`(버킷 A), 그 외 4xx/5xx/네트워크 오류/디코딩 오류는 토큰을 보존한 채 `null` 반환(버킷 B)으로 정확히 분기한다. **다만 C1의 Bearer 버그로 인해 이 로직에 실제로 도달하는 경우가 극히 제한적이라는 점은 별개 이슈다.**
- **OkHttp 동기 계약 준수**: `refreshAccessToken`을 suspend가 아닌 `Call<RefreshResponse>`로 선언(`AgentApi.kt:36-39`)하고 `TokenAuthenticator`에서 `.execute()`로 동기 호출해, `Authenticator`의 블로킹 콜백 계약 안에서 `runBlocking` 없이 깔끔하게 처리했다. 설계서가 제시한 `runBlocking` 방식보다 더 관용적인 구현이다.
- **토큰/비밀번호 로깅 없음**: `AuthInterceptor`/`TokenAuthenticator`/`AuthRepository`/`AuthViewModel`/`TokenStore` 전체에서 토큰 문자열이나 `LoginRequest` 객체 전체를 로그로 출력하는 코드가 없음을 확인했다(`Log.e`는 `throwable.message`만 사용).
- **Base URL/Secret 신규 하드코딩 없음**: `ApiConfig.kt`의 값 변경은 기존 관례(로컬 IP 상수) 그대로이며, 새로운 Secret이 소스에 추가되지 않았다.
- **빌드/유닛테스트 성공**(§8 참고).

---

## 8. 빌드 및 테스트 결과

```
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest
→ BUILD SUCCESSFUL (52s)

JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug
→ BUILD SUCCESSFUL (22s)
```

`app/src/test`에는 템플릿 기본 테스트(`2+2=4`)만 존재해 인증 로직에 대한 실질적 유닛 테스트 검증은 아니며, 컴파일 성공만을 의미한다. 인증 관련 신규 유닛 테스트는 이번 리뷰 범위에서 추가하지 않았다(작업 제한 §8 준수).

### 런타임 시나리오 검증 여부

아래 항목은 모두 **직접 실행 검증하지 못했다.** 이유: 이번 작업은 코드 수정이 금지된 정적 리뷰이며, 실기기/에뮬레이터 구동과 백엔드 서버 기동, 실제 계정 로그인/토큰 만료 유도가 필요한 실측 검증까지는 이번 세션 범위에서 수행하지 않았다. 아래 판단은 모두 코드 근거 기반 정적 분석 결과이며, "성공했다"고 추정하지 않는다.

| 시나리오 | 검증 여부 | 비고 |
|---|---|---|
| 정상 로그인 및 Main 화면 이동 | 미검증(정적 분석상 로직은 타당) | 실제 백엔드 응답으로 검증 필요 |
| 빈 입력과 잘못된 비밀번호 | 미검증 | 빈 입력은 로직상 처리됨(m1 참고), 잘못된 비밀번호 시 M2로 인해 메시지가 부정확할 수 있음 |
| 로그인 버튼 연속 클릭 | 미검증(코드 근거로는 방지됨, Good 참고) | |
| 앱 재실행 시 인증 상태 복원 | **미구현 확인**(C2) | 실행할 필요 없이 코드 부재로 확정 |
| 만료된 Access Token으로 요청 후 Refresh 성공 | **실패할 것으로 강하게 예상**(C1) | Bearer 공백 버그로 인해 정상 동작 불가능 |
| 동시 401 발생 시 Refresh 한 번 실행 | 검증 불가(C1로 인해 Refresh 자체가 호출되지 않음) | |
| Refresh 400/401 시 토큰 삭제 및 Login 상태 전환 | 토큰 삭제는 코드상 확인됨, 화면 전환은 **미구현 확인**(C2) | |
| Refresh 네트워크 오류 시 기존 토큰 유지 | 코드 근거로는 타당(Good 참고) | 실측 미검증 |
| Refresh 후 재시도 요청이 다시 401일 때 중단 | 코드 근거로는 타당(무한루프 방지 로직 자체는 정상) | C1로 인해 애초에 Refresh 성공 경로에 도달하지 못함 |

---

## 9. 설계·API Contract·Backend·iOS와 실제 구현의 차이

### 9.1 Endpoint 경로 — 설계서/API 명세서(xlsx) 모두 `/api/auth/*`를 문서화하지만 실제 백엔드는 `/auth/*`
- `backend-api-spec.xlsx`(sharedStrings 직접 파싱 확인): `/api/auth/login`, `/api/auth/refresh` 등으로 기록되어 있고 "코드 미구현(라우트 없음), Draft" 상태로 명시되어 있다. 이는 **사실과 다르다** — 실제로는 완전히 구현되어 있다.
- 설계서(`android-login-auth.md` §3)도 작성 시점(2026-08-12) 기준 `src/index.js`가 `app.use("/api/auth", authRouter)`였다고 기록했으나, **현재 실제 `src/index.js:20`은 `app.use("/auth", authRouter)`**로 `/api` 접두사가 없다.
- Android 실제 구현(`AgentApi.kt`의 `@POST("auth/login")`, `@POST("auth/refresh")`, `AuthInterceptor`의 제외 경로 `/auth/login`, `/auth/refresh`)은 **현재 실제 백엔드 코드와 정확히 일치한다.** 워크플로 지시("Backend 실제 구현 우선")에 따라 이는 Android 코드의 결함이 아니라, 설계서와 API 명세서(xlsx) 두 문서가 모두 최신 백엔드 코드를 반영하지 못한 상태라는 문서 쪽 문제로 기록한다.

### 9.2 Request/Response 필드
- `LoginRequest(email,password)`, `LoginResponse/RefreshResponse(accessToken,refreshToken,expiresAt,user{id,email})`, `RefreshRequest(refreshToken)` 모두 `authService.js`의 `toAuthResponse()` 실제 응답 구조와 정확히 일치함을 확인했다.
- 에러 응답 구조 `AuthErrorResponse(error,message)`도 실제 백엔드(`authController.js`의 `respondAuthError`)와 필드가 일치하나, Android 쪽에서 이 DTO를 실제로 파싱해 쓰는 코드가 없다(M2).

### 9.3 HTTP Status 분류
- 백엔드는 로그인/리프레시 실패를 400(`validation_failed`) 또는 401(`invalid_credentials`/`invalid_refresh_token`)로만 응답하며 그 외 세분화된 코드가 없다. `TokenAuthenticator`가 "400 또는 401만 명시적 거절"로 판정하는 분기는 이와 정확히 일치한다(Good).

### 9.4 `/api/plan` 관련 기존 불일치 (auth Feature 범위 밖, 기록만)
- 실제 백엔드 `src/routes/api.js`에는 `/ping`, `/ask`, `/review`만 등록되어 있고 **`/plan` 라우트 자체가 존재하지 않는다**(`apiController.js`에 `generatePlan` 함수는 있으나 라우터에 연결되지 않음). Android `AgentApi.requestPlan()`이 호출하는 `POST api/plan`은 현재 백엔드에서 404가 될 것으로 보인다.
- 이번 인증 작업이 `AuthInterceptor`의 `AUTH_EXCLUDED_PATHS`에 `/api/plan`을 포함시킨 것은 워크플로 §4의 지시("`/api/plan`은 현재 정책상 인증 제외 요청")를 그대로 따른 것으로 코드 자체는 지시에 맞으나, 그 경로가 실제로는 존재하지 않는 라우트라는 점은 auth Feature와 무관하게 이미 존재하던 백엔드/Android 간 불일치이므로 임의로 변경하지 않고 기록만 한다.

### 9.5 iOS 대비 차이
- iOS는 앱 재시작 시 세션 복원 로직이 없다는 documented gap이 있지만, 최소한 로그인 성공/강제 로그아웃 시점에는 `PrivateAgentApp`이 `authState.isLoggedIn`을 실시간 관찰해 화면을 전환한다. Android는 그 관찰 메커니즘 자체가 없어(C2) 강제 로그아웃 후 화면 전환이 안 된다는 점에서 iOS보다 더 취약하다.
- iOS `LoginViewModel.errorMessage(for:)`는 `AuthErrorResponse.message`를 명확히 매핑해 사용하는 반면, Android는 이 매핑이 없다(M2) — iOS 대비 후퇴한 지점이다.
- 두 플랫폼 모두 토큰 쌍 저장을 사실상 원자적으로 처리한다는 점(iOS: 실패 시 rollback / Android: 단일 `commit()` 트랜잭션)은 동등한 수준으로 확인된다.
- iOS의 동시 401 dedup은 실제 보호된 엔드포인트가 없어 정적 분석으로만 검증됐다는 점이 iOS 리뷰 문서에 명시돼 있다. Android는 보호된 엔드포인트(`/api/ask`, `/api/review`)가 실제로 존재하므로 원칙적으로 실측이 가능하지만, C1 버그로 인해 이번 세션에서는 실측을 진행하지 않았다(실행해도 의미 있는 성공 결과를 얻지 못했을 것으로 판단됨).

---

## 10. Summary

- **설계 일치 여부**: 부분 일치. 로그인 성공 조건, 토큰 쌍 원자적 저장, 재시도 횟수 제한, Refresh 실패 분류 로직의 "형태"는 설계와 일치하지만, 핵심 동작인 Refresh 실행 자체(C1)와 앱 상태 복원/강제 로그아웃 화면 전환(C2)이 설계서 §10~§13이 요구하는 수준으로 구현되지 않았다.
- **API Contract 일치 여부**: 실제 백엔드 코드 기준으로는 Endpoint/필드/에러 형식 모두 일치한다. 다만 설계서와 API 명세서(xlsx) 두 문서가 현재 백엔드의 실제 마운트 경로(`/auth/*`)를 반영하지 못해 오래된 상태다(9.1).
- **Architecture 준수 여부**: `UI → ViewModel → Repository → AgentApi` 3계층 구조와 기존 `object ... by lazy`/`UiState + mutableStateOf` 컨벤션은 유지됐다. 다만 TokenStore 중복 생성(M3), AuthRepository의 부분적 책임(m7) 등 경미한 이탈이 있다.
- **보안 평가**: 토큰/비밀번호를 평문 로그로 남기는 코드는 없으며(Good), EncryptedSharedPreferences 기반 저장 자체는 안전하다. 다만 alpha 버전 라이브러리 사용(m5)은 배포 전 재검토가 필요하다. 심각한 인증 우회나 데이터 노출은 발견되지 않았다.
- **유지보수성 평가**: 죽은 코드(`AuthErrorResponse` 미사용, `getTokenStore()` 미사용, 주석 처리된 `@Preview`)가 몇 군데 남아 있어 정리가 필요하다.
- **빌드/테스트**: `./gradlew :app:testDebugUnitTest`, `./gradlew :app:assembleDebug` 모두 `BUILD SUCCESSFUL`.
- **최종 평가**: Critical 2건(Refresh 메커니즘 미동작, 앱 인증 상태 복원 부재)이 이번 Feature의 핵심 기능을 무력화하고 있어 **현재 상태로는 기능이 완성됐다고 볼 수 없다.**

### 최종 판정

- **현재 상태로 커밋 가능한가**: **불가.** C1(Refresh 메커니즘 미동작), C2(앱 인증 상태 복원 부재) 두 Critical 이슈가 이번 Feature의 핵심 요구사항을 충족하지 못한 상태로 남아 있다.
- **Critical/Major 수정이 필요한가**: 예. C1·C2는 필수, M1~M3는 권장.
- **재리뷰가 필요한가**: 예. 특히 C1 수정 후에는 실기기/에뮬레이터에서 Access Token을 강제로 만료시켜 "401 → Refresh → 재시도 성공" 흐름을 실제로 관찰하는 런타임 검증이 반드시 필요하다(이번 세션에서는 미수행).
- **앱 인증 상태 복원까지 구현됐는가**: **아니오.** 워크플로 §4의 필수 범위이자 §5.7 전체 항목이 구현되지 않았다(C2).
- **다음 Android 기능으로 진행 가능한가**: **권장하지 않음.** 이번 Feature의 핵심 기능(자동 토큰 갱신, 세션 복원)이 동작하지 않는 상태에서 후속 기능을 쌓으면 결함이 전파될 위험이 크다. C1·C2를 먼저 해결하고 재리뷰를 통과한 뒤 진행을 권장한다.

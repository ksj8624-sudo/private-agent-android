# Authentication Android Fix

## 1. 작업 목표

Android Authentication 1차 코드 리뷰에서 확인된 핵심 결함을 수정하고, 로그인·토큰 저장·401 Refresh·세션 복원 흐름을 설계서와 실제 Backend 규격에 맞게 완성한다.

이번 작업은 리뷰 결과에 근거한 수정과 검증만 수행한다. 인증 범위를 넘어서는 기능 추가나 광범위한 Architecture 변경은 하지 않는다.

---

## 2. 작업 대상 프로젝트

```text
/Users/kimseongjin/Desktop/workspace/private-agent
```

---

## 3. 반드시 먼저 확인할 자료

다음 자료를 처음부터 끝까지 읽고 실제 코드와 비교한 뒤 수정한다.

### 1차 Review 결과

```text
/Users/kimseongjin/Desktop/workspace/private-agent/docs/reviews/authentication-android-review.md
```

### Android 인증 설계서

```text
/Users/kimseongjin/Desktop/workspace/private-agent/docs/designs/android-login-auth.md
```

### Android Architecture

```text
/Users/kimseongjin/Desktop/workspace/private-agent/docs/architecture/android-project-overview.md
```

### API Contract

```text
/Users/kimseongjin/Desktop/workspace/private-agent-backend/docs/backend-api-spec.xlsx
```

### Backend 실제 구현

```text
/Users/kimseongjin/Desktop/workspace/private-agent-backend
```

### iOS 참고 구현 및 Review

```text
/Users/kimseongjin/Desktop/workspace/ios/PrivateAgent
/Users/kimseongjin/Desktop/workspace/ios/PrivateAgent/docs/reviews/authentication-ios-review.md
/Users/kimseongjin/Desktop/workspace/ios/PrivateAgent/docs/reviews/authentication-ios-rereview.md
```

API 문서와 Backend 코드가 다르면 Backend 실제 구현을 우선한다. 현재 실제 인증 경로는 `/auth/login`, `/auth/refresh`임을 코드로 다시 확인한다. API Spec 또는 설계서의 오래된 `/api/auth/*` 표기를 근거로 정상 동작하는 Android Endpoint를 바꾸지 않는다.

---

## 4. 수정 전 확인

수정 전에 다음을 실행하여 현재 변경 범위를 기록한다.

```text
git status
git diff
git diff --cached
```

사용자의 기존 변경사항을 보존하고 Authentication Feature와 무관한 파일은 수정하지 않는다.

---

## 5. 필수 수정 범위

### F1. Bearer 접두사 오류 수정

1차 Review C1을 수정한다.

- `TokenAuthenticator`의 Bearer 접두사에 공백을 포함한다.
- Access Token 추출과 Authorization 헤더 생성에서 같은 규칙을 사용한다.
- 기존 `AuthInterceptor`의 `Bearer {token}` 형식과 일치시킨다.
- 만료된 토큰과 현재 저장된 토큰 비교 시 공백 때문에 다른 세대로 오판하지 않도록 한다.
- 토큰 값 자체는 trim 또는 임의 변형하지 않는다.

완료 조건:

```text
최초 401
→ 다른 요청이 이미 갱신했다는 오판 없음
→ Refresh API 호출
→ 새 토큰 쌍 저장
→ Authorization: Bearer {newAccessToken}
→ 원 요청 최대 1회 재시도
```

### F2. 인증 API의 Authenticator 재진입 방지

1차 Review M1을 수정한다.

- `TokenAuthenticator.authenticate()` 시작 지점에서 실패한 요청의 실제 경로를 확인한다.
- `/auth/login`과 `/auth/refresh`이면 즉시 `null`을 반환한다.
- 로그인 401을 Access Token 만료로 오인해 재시도하지 않는다.
- Refresh API는 이미 별도의 `refreshClient`를 사용하므로 이 분리 구조를 유지한다.
- `/auth/refresh` 경로 가드는 방어적으로 유지하되, Refresh API를 인증 클라이언트에 다시 연결하지 않는다.

### F3. TokenStore 단일 인스턴스 사용

1차 Review M3을 수정한다.

- `AuthViewModel`에서 `TokenStore(application.applicationContext)`를 새로 만들지 않는다.
- `PrivateAgentApplication`에서 초기화한 `NetworkModule`의 TokenStore를 재사용한다.
- 로그인, `AuthInterceptor`, `TokenAuthenticator`가 동일한 TokenStore 인스턴스를 사용하도록 한다.
- `NetworkModule.getTokenStore()` 또는 현재 구조에 맞는 동일한 단일 접근 지점을 사용한다.
- 불필요해진 import와 죽은 초기화 코드를 제거한다.

### F4. 로그인 오류 분류와 사용자 메시지 개선

1차 Review M2를 수정한다.

- `HttpException`, 네트워크 오류, 타임아웃, 응답 파싱 오류를 구분한다.
- Backend의 `AuthErrorResponse(error, message)`를 필요한 경우 안전하게 파싱한다.
- 서버의 원본 예외 문자열이나 Retrofit/OkHttp 내부 메시지를 사용자에게 그대로 표시하지 않는다.
- 서버 `message`를 무조건 신뢰하지 말고, 알려진 `error` 코드와 HTTP Status에 대해 사용자용 문구를 매핑한다.
- 잘못된 계정 정보, 입력 검증 실패, 네트워크 장애, 기타 시스템 오류를 최소한 구분한다.
- 토큰, 비밀번호, 요청 Body 및 Authorization 헤더를 로그에 남기지 않는다.
- 동일 오류를 중복으로 `Log.e` 하지 않는다.

권장 사용자 메시지 예:

- 인증 정보 거절: `이메일 또는 비밀번호를 확인해 주세요.`
- 입력 검증 실패: Backend의 안전한 검증 메시지 또는 고정된 입력 확인 문구
- 네트워크 오류/타임아웃: `네트워크 상태를 확인한 후 다시 시도해 주세요.`
- 기타 오류: `로그인 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.`

### F5. 앱 전역 인증 상태 및 세션 복원

1차 Review C2에서 확인된 미구현 범위를 Android 설계서에 맞게 구현한다.

최소 상태는 다음 의미를 구분해야 한다.

```text
Unknown 또는 Initializing
LoggedIn
LoggedOut
```

구현 요구사항:

- 앱 전체에서 관찰할 수 있는 인증 상태 컴포넌트를 둔다.
- 별도 DI 프레임워크를 새로 도입하지 않는다.
- 앱 시작 시 Access Token과 Refresh Token을 모두 확인한 뒤 초기 화면을 결정한다.
- 토큰 두 개가 모두 존재하면 로그인 상태로 판단한다.
- 둘 중 하나만 존재하는 불완전한 상태이면 토큰을 모두 삭제하고 로그아웃 상태로 전환한다.
- 초기화가 끝나기 전에 잘못된 Login 또는 Main 화면이 잠깐 노출되지 않도록 한다.
- 로그인과 토큰 저장이 모두 성공한 후 전역 상태를 LoggedIn으로 변경한다.
- Refresh Token 없음, Refresh 400/401, 새 토큰 쌍 저장 실패처럼 명시적인 인증 거절 상황에서는 토큰을 삭제하고 전역 상태를 LoggedOut으로 변경한다.
- Refresh 네트워크 오류, 타임아웃, 5xx, 일시적 파싱 오류에서는 기존 토큰을 즉시 삭제하거나 강제 로그아웃하지 않는다.
- Main 화면은 전역 LoggedOut 상태를 관찰해 Login 화면으로 이동하고 현재 Main Activity를 종료한다.
- Login 화면은 초기 세션 복원 결과가 LoggedIn이면 Main 화면으로 이동하고 Login Activity를 종료한다.
- 화면 이동이 재구성이나 상태 재전달로 중복 실행되지 않도록 한다.

현재 작업 범위에서 사용자가 직접 누르는 Logout UI/API와 `/auth/me`는 구현하지 않는다.

---

## 6. 함께 정리할 Minor 항목

필수 수정과 직접 연결되며 위험이 낮은 다음 항목만 함께 정리한다.

- 로그인 입력 검증을 `isEmpty()` 대신 `isBlank()` 기준으로 보완한다.
- 이메일 앞뒤 공백은 요청 전에 제거하되 비밀번호는 임의로 trim하지 않는다.
- Authorization 헤더 이름 표기를 파일 간 일관되게 맞춘다.
- 중복 `Log.e`를 제거한다.
- TokenStore 읽기에서 발생 가능한 예외를 설계된 실패 유형에 맞게 처리한다.
- 현재 `LoginScreen` 시그니처와 맞지 않는 주석 처리 Preview 등 명백한 죽은 코드를 제거한다.
- 사용하지 않는 import와 사용되지 않는 인증 코드를 제거한다.

다음 항목은 이번 수정에서 확장하지 않는다.

- Hilt 등 DI Framework 도입
- Repository/Network 계층의 대규모 재설계
- `AgentApi` 인터페이스의 역할별 파일 분리
- `AuthRepository` 전체 리팩터링
- Security Crypto 저장 기술 교체
- Base URL을 `BuildConfig`로 이전
- `/api/plan` Backend Route 수정
- Backend API Spec 및 iOS 코드 수정

---

## 7. 동시 Refresh 안전성 확인

F1 수정 후 기존 동시성 로직을 다시 검토하고 필요한 최소 수정만 수행한다.

- 최초 401 요청만 Refresh를 수행하는가
- 다른 401 요청은 동기화 영역에서 기다리는가
- 대기 중 다른 요청이 갱신한 Access Token을 감지해 그 토큰으로 재시도하는가
- Refresh API가 동기화 영역 안에서 호출되는 현재 방식에 교착상태가 없는가
- Refresh 전용 `OkHttpClient`가 인증용 Client와 분리되어 있는가
- 원 요청의 `priorResponse`를 세어 한 번만 재시도하는가
- 재시도도 401이면 `null`을 반환해 무한 반복을 막는가

기존 구현이 위 조건을 만족한다면 동시성 구조를 불필요하게 다시 작성하지 않는다.

---

## 8. 테스트

가능한 범위에서 인증 관련 테스트를 추가하거나 보완한다. 테스트 가능성을 위해 Production Architecture를 광범위하게 변경하지 않는다.

최소 확인 대상:

- Bearer 헤더 문자열과 토큰 추출
- 인증 제외 경로
- 원 요청 최대 1회 재시도
- 다른 요청이 갱신한 토큰 재사용
- Refresh 400/401 시 토큰 삭제 및 LoggedOut 전환
- Refresh 네트워크 오류 시 토큰 유지
- 토큰 쌍 일부 존재 시 앱 시작에서 전체 삭제 및 LoggedOut
- 정상 토큰 쌍 존재 시 LoggedIn 복원
- 로그인 실패 오류 메시지 매핑

테스트 추가가 현재 구조에서 과도한 Production 변경을 요구한다면 무리하게 추가하지 말고, 빌드 후 수동 검증 항목으로 명확히 보고한다.

수정 후 최소한 다음을 실행한다.

```text
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Android Studio JBR이 필요하면 다음 JAVA_HOME을 사용할 수 있다.

```text
/Applications/Android Studio.app/Contents/jbr/Contents/Home
```

---

## 9. 런타임 검증

Backend 서버와 에뮬레이터 또는 실기기 사용이 가능한 경우 다음 시나리오를 검증한다.

1. 정상 계정 로그인 후 Main 화면 이동
2. 잘못된 비밀번호에서 Refresh가 호출되지 않고 로그인 오류 표시
3. 앱 완전 종료 후 재실행 시 Main 화면 복원
4. Access Token만 의도적으로 만료 또는 변조한 상태에서 보호 API 호출
5. 401 후 Refresh 한 번 호출
6. 새 토큰 쌍 저장 후 원 요청 재시도 성공
7. 재시도 요청도 401이면 추가 Refresh 없이 중단
8. Refresh Token을 무효화한 뒤 400/401 응답 시 토큰 삭제 및 Login 화면 이동
9. Refresh 중 네트워크를 차단했을 때 토큰 유지

런타임 검증을 수행하려면 토큰이나 비밀번호를 로그에 출력하지 않는다. 검증하지 못한 항목을 성공했다고 추정하지 않는다.

---

## 10. 문서 불일치 처리

이번 코드 수정에서는 다음 문서 불일치를 코드에 역반영하지 않는다.

```text
오래된 설계/API 문서: /api/auth/login, /api/auth/refresh
현재 Backend 실제 구현: /auth/login, /auth/refresh
```

Android의 `/auth/login`, `/auth/refresh` 구현은 현재 Backend 코드와 일치하므로 유지한다.

문서 최신화는 별도 후속 작업으로 보고하되, 이번 Fix 과정에서 Backend 저장소, API Spec 또는 iOS 문서를 수정하지 않는다.

---

## 11. 작업 제한

- Authentication Feature와 직접 관련된 Android 코드만 수정한다.
- 기존 사용자 변경사항을 삭제하거나 덮어쓰지 않는다.
- Backend 및 iOS 코드를 수정하지 않는다.
- API Endpoint를 변경하지 않는다.
- 새로운 DI Framework를 도입하지 않는다.
- 대규모 Refactoring을 하지 않는다.
- 새 Review 결과 문서를 작성하지 않는다. Review 문서는 다음 재리뷰 단계에서 작성한다.
- Git commit과 push를 하지 않는다.
- 강제 reset, checkout, clean 등 작업 트리를 손상시키는 명령을 사용하지 않는다.

---

## 12. 완료 보고

작업 완료 시 채팅에는 다음을 간단히 보고한다.

- 수정한 파일 목록
- F1~F5 각각의 수정 결과
- 함께 처리한 Minor 항목
- 빌드 및 테스트 결과
- 런타임 검증 결과와 미검증 항목
- 남은 이슈와 문서 불일치
- 재리뷰 진행 가능 여부

코드 변경 내역은 파일과 핵심 변경을 근거로 보고하고, 성공하지 않은 항목을 완료했다고 표현하지 않는다.

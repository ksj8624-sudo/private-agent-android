# Authentication Android Re-Review

## 1. 작업 목표

Android Authentication 1차 Review에서 확인된 Critical/Major 이슈와 이후 Fix Workflow에 따른 수정사항을 재검증한다.

이번 작업은 코드 수정이 아닌 재리뷰다. 실제 변경 코드, 신규 테스트, 빌드 결과와 사용자 실기기 검증 결과를 근거로 현재 상태에서 커밋 가능한지 최종 판정한다.

---

## 2. 작업 대상 프로젝트

```text
/Users/kimseongjin/Desktop/workspace/private-agent
```

---

## 3. 반드시 먼저 확인할 자료

### 공통 Review 규칙

```text
/Users/kimseongjin/Desktop/workspace/ai-agent-lab/docs/prompts/tasks/review.md
```

### Android 인증 설계서

```text
/Users/kimseongjin/Desktop/workspace/private-agent/docs/designs/android-login-auth.md
```

### Android Architecture

```text
/Users/kimseongjin/Desktop/workspace/private-agent/docs/architecture/android-project-overview.md
```

### 1차 Review 결과

```text
/Users/kimseongjin/Desktop/workspace/private-agent/docs/reviews/authentication-android-review.md
```

### Fix Workflow

```text
/Users/kimseongjin/Desktop/workspace/private-agent/docs/workflow/fix/authentication-android-fix.md
```

### API Contract와 Backend 실제 구현

```text
/Users/kimseongjin/Desktop/workspace/private-agent-backend/docs/backend-api-spec.xlsx
/Users/kimseongjin/Desktop/workspace/private-agent-backend
```

### iOS 참고 구현 및 Review

```text
/Users/kimseongjin/Desktop/workspace/ios/PrivateAgent
/Users/kimseongjin/Desktop/workspace/ios/PrivateAgent/docs/reviews/authentication-ios-review.md
/Users/kimseongjin/Desktop/workspace/ios/PrivateAgent/docs/reviews/authentication-ios-rereview.md
```

API 문서와 Backend 코드가 다르면 Backend 실제 구현을 우선한다. 현재 Backend의 `/auth/login`, `/auth/refresh`와 일치하는 Android 코드를 오래된 `/api/auth/*` 문서 표기에 맞추어 변경하지 않는다.

---

## 4. 재리뷰 범위

먼저 다음 명령으로 1차 Review 이후의 실제 변경 범위를 확인한다.

```text
git status
git diff
git diff --cached
```

현재 미커밋 변경사항 전체를 확인하되, Authentication Feature와 무관한 기존 사용자 변경사항은 평가 대상에서 분리한다.

특히 다음 수정·신규 파일을 직접 읽는다.

- `TokenAuthenticator.kt`
- `AuthInterceptor.kt`
- `AuthHeader.kt`
- `NetworkModule.kt`
- `SessionManager.kt`
- `AuthViewModel.kt`
- `LoginErrorMapper.kt`
- `LoginActivity.kt`
- `MainActivity.kt`
- `PrivateAgentApplication.kt`
- `AndroidManifest.xml`
- `TokenStore.kt`
- `AgentApi.kt`
- `AuthRepository.kt`
- 인증 관련 DTO
- 신규 인증 유닛테스트 5개 파일

파일명이 실제로 다르면 프로젝트 안에서 해당 책임을 수행하는 파일을 찾아 정확한 경로를 결과에 기록한다.

---

## 5. 1차 Review Issue 해결 검증

### C1. Bearer 접두사 공백 누락

다음을 코드 근거로 확인한다.

- `Bearer ` 접두사가 공백을 포함하는가
- `AuthInterceptor`와 `TokenAuthenticator`가 동일한 상수를 공유하는가
- Access Token 추출과 헤더 생성 규칙이 일치하는가
- 최초 401에서 현재 토큰과 요청 토큰을 잘못 비교하지 않는가
- Refresh 후 `Authorization: Bearer {newAccessToken}`으로 원 요청을 재시도하는가

### C2. 앱 인증 상태 복원 및 강제 로그아웃 화면 전환

다음을 코드 근거로 확인한다.

- 앱 전역 상태가 Initializing/LoggedIn/LoggedOut을 구분하는가
- `PrivateAgentApplication`과 `NetworkModule`의 초기화 순서가 안전한가
- 앱 시작 시 Access/Refresh Token 쌍을 확인하는가
- 두 토큰 중 하나만 존재하면 모두 삭제하고 LoggedOut 처리하는가
- 두 토큰이 모두 존재하면 LoggedIn으로 복원하는가
- 초기 상태 판단 전 Login 또는 Main 화면이 잘못 노출되지 않는가
- 로그인 성공과 토큰 쌍 저장 완료 후 LoggedIn으로 전환하는가
- Refresh 400/401, Refresh Token 없음, 새 토큰 저장 실패 시 LoggedOut으로 전환하는가
- Main 화면이 LoggedOut을 관찰해 Login 화면으로 이동하고 자신을 종료하는가
- Login 화면이 LoggedIn을 관찰해 Main 화면으로 이동하고 자신을 종료하는가
- 상태 재전달 또는 Compose 재구성으로 화면 이동이 중복되지 않는가

### M1. 로그인/Refresh 요청의 Authenticator 재진입

- `/auth/login`, `/auth/refresh` 경로에서 `authenticate()`가 즉시 `null`을 반환하는가
- 로그인 401이 Access Token 만료로 오인되지 않는가
- Refresh 요청은 별도의 인증 없는 `refreshClient`를 계속 사용하는가
- Refresh가 다시 Authenticator를 호출하는 순환이 없는가

### M2. 로그인 오류 메시지 분류

- 알려진 Backend error 코드와 HTTP Status를 안전한 사용자 문구로 매핑하는가
- `HttpException`, `IOException`, 기타 오류를 구분하는가
- 서버 원문 예외, 토큰, 비밀번호, Request Body 또는 Authorization 헤더가 사용자 UI나 로그에 노출되지 않는가
- `AuthErrorResponse`가 실제로 안전하게 사용되는가
- 동일 오류를 중복으로 로깅하지 않는가

### M3. TokenStore 중복 생성

- 로그인, Interceptor, Authenticator 및 SessionManager가 같은 TokenStore 인스턴스를 사용하는가
- `AuthViewModel`이 TokenStore를 직접 새로 생성하지 않는가
- 초기화되지 않은 NetworkModule 접근 가능성이 없는가

1차 Review의 각 C/M Issue에 대해 `해결`, `부분 해결`, `미해결` 중 하나로 명확히 판정하고 코드 근거를 기록한다.

---

## 6. 수정으로 인한 회귀 검증

다음을 추가로 확인한다.

- 원 요청은 Refresh 후 최대 한 번만 재시도되는가
- 재시도 요청도 401이면 추가 Refresh 없이 중단되는가
- 동시에 여러 요청이 401이어도 Refresh가 한 번만 실행되는가
- 기다리던 요청이 다른 요청이 갱신한 Access Token을 재사용하는가
- Refresh 전용 Client와 인증용 Client 사이에 교착 또는 순환 가능성이 없는가
- Refresh 400/401만 명시적 인증 거절로 분류하는가
- 기타 4xx, 5xx, 네트워크 오류, 타임아웃, 디코딩 오류에서는 기존 토큰을 즉시 삭제하지 않는가
- 새 토큰 쌍 저장 실패를 인증 성공으로 처리하지 않는가
- 로그인 중복 제출 방지가 유지되는가
- 이메일만 trim하고 비밀번호는 임의로 변경하지 않는가
- `/health`, `/auth/login`, `/auth/refresh`, `/api/plan` 인증 제외 정책이 유지되는가
- 기존 비인증 기능과 Main 화면 동작에 회귀가 없는가
- SessionManager 또는 Activity가 Context/Coroutine/Observer를 잘못 보관해 누수 가능성을 만들지 않는가

새로운 Critical/Major/Minor가 발견되면 1차 Issue 해결 여부와 별도로 기록한다.

---

## 7. 신규 테스트 검증

추가된 5개 테스트 파일과 20개 테스트 케이스를 실제로 읽는다.

다음 항목을 확인한다.

- 테스트가 실제 Production 코드를 검증하는가
- 단순히 상수나 테스트 내부 복제 로직만 검증하지 않는가
- Bearer 헤더 생성과 토큰 추출
- 인증 제외 경로
- 재시도 횟수 제한
- 다른 요청이 갱신한 토큰 재사용
- Refresh 실패 시 Token/Session 처리
- 앱 시작 시 Token 쌍 복원 판단
- 불완전한 Token 쌍 정리
- 로그인 오류 메시지 매핑
- 테스트가 우연히 통과하거나 핵심 분기를 누락하지 않는가

테스트 개수만으로 완료 판정하지 말고 실제 assertion과 Production 연결 근거를 기록한다.

---

## 8. 빌드 및 테스트 실행

코드를 수정하지 않은 상태에서 다음을 실행한다.

```text
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

필요하면 다음 JBR을 JAVA_HOME으로 사용한다.

```text
/Applications/Android Studio.app/Contents/jbr/Contents/Home
```

빌드와 테스트의 실제 결과, 실행된 테스트 수, 실패·경고를 기록한다. 테스트 실패를 수정하지 않는다.

---

## 9. 실기기 검증 결과 취급

사용자가 Android 실기기에서 인증 런타임 테스트를 완료했다고 보고했다.

최소 확인 대상은 다음과 같다.

- 정상 로그인 및 Main 화면 이동
- 앱 완전 종료 후 재실행 시 Main 화면 복원
- 만료된 Access Token에서 401 → Refresh → 원 요청 재시도 성공
- 무효한 Refresh Token 또는 Refresh 400/401에서 토큰 삭제 및 Login 화면 전환

Claude가 이번 재리뷰 세션에서 직접 실기기 테스트를 수행하지 않았다면 이를 `Claude 직접 검증`으로 표현하지 않는다. 다음처럼 구분한다.

```text
사용자 실기기 검증: 완료 보고됨
Claude 독립 런타임 검증: 수행 여부와 결과를 별도 기록
정적 코드/유닛테스트/빌드 검증: Claude가 실제 수행한 결과 기록
```

사용자의 완료 보고와 코드 근거가 충돌하면 충돌 내용을 기록하고 추측으로 통과시키지 않는다.

---

## 10. 문서 불일치

다음 기존 불일치는 Android 코드의 결함으로 다시 판정하지 않는다.

```text
오래된 설계/API 문서: /api/auth/login, /api/auth/refresh
현재 Backend 실제 구현: /auth/login, /auth/refresh
```

Android 코드가 현재 Backend 실제 구현과 일치하면 정상으로 판단한다.

`/api/plan` Backend Route 부재는 기존 별도 이슈로 기록할 수 있지만, 이번 Authentication 재리뷰의 Critical/Major로 중복 평가하지 않는다.

---

## 11. 결과 분류

결과를 다음 등급으로 분류한다.

- **Critical**: 빌드 불가, 보안 문제, 데이터 손상 또는 심각한 인증 우회 가능성
- **Major**: 실제 인증 동작 오류, 토큰 상태 불일치, 무한 Refresh/재시도, 동시성 또는 화면 상태 전환 문제
- **Minor**: 현재 기능을 막지 않지만 개선이 필요한 문제
- **Good**: 실제 코드·테스트·빌드 근거로 정상 확인한 항목
- **Summary**: 설계, Backend, iOS 정책, Architecture, 보안 및 최종 판정

각 Issue에는 반드시 다음을 포함한다.

1. 파일과 위치
2. 문제 코드 또는 코드 근거
3. 발생 가능한 시나리오
4. 설계 또는 Backend 정책과의 차이
5. 권장 수정 방향
6. 커밋 전 수정 필요 여부

코드 근거가 없는 우려는 Issue로 확정하지 말고 확인 필요 항목으로 분리한다.

---

## 12. 작업 제한

- Android 소스코드를 수정하지 않는다.
- 테스트 코드를 추가하거나 수정하지 않는다.
- Refactoring을 하지 않는다.
- Backend, iOS, API Spec 및 기존 문서를 수정하지 않는다.
- Git commit과 push를 하지 않는다.
- Review 결과 문서 작성 외에는 파일을 변경하지 않는다.

---

## 13. 재리뷰 결과 저장

결과를 채팅으로만 보고하지 말고 다음 경로에 Markdown 문서로 저장한다.

```text
/Users/kimseongjin/Desktop/workspace/private-agent/docs/reviews/authentication-android-rereview.md
```

문서에는 다음을 포함한다.

1. 재리뷰 일시와 범위
2. 참고한 문서와 실제 코드
3. 1차 Review 이후 git 변경 범위
4. C1/C2/M1/M2/M3 해결 여부와 코드 근거
5. 신규 Critical/Major/Minor/Good
6. 신규 테스트의 실제 검증 범위
7. 빌드 및 테스트 실행 결과
8. 사용자 실기기 검증과 Claude 직접 검증의 구분
9. 문서·Backend·iOS와 실제 구현의 차이
10. 남은 위험과 미검증 항목
11. 최종 판정

Issue가 없더라도 빈 문서를 만들지 말고 각 이슈가 해결됐다고 판단한 코드 근거를 기록한다.

최종 판정에는 다음을 명시한다.

- Critical 0인가
- Major 0인가
- 현재 상태로 커밋 가능한가
- 추가 수정 또는 재리뷰가 필요한가
- Authentication Feature가 완료됐는가
- 다음 Android 기능으로 진행 가능한가

---

## 14. 채팅 완료 보고

채팅에는 다음만 간단히 요약한다.

- 저장한 재리뷰 문서 경로
- 기존 C1/C2/M1/M2/M3 해결 여부
- 신규 Critical/Major/Minor 개수
- 빌드 및 테스트 결과
- 사용자 실기기 검증 반영 여부
- 커밋 가능 여부
- 추가 재리뷰 필요 여부
- 다음 작업 진행 가능 여부

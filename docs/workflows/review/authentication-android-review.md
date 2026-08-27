# Authentication Android Review

## 1. 작업 목표

Private Agent Android 프로젝트에 구현된 로그인 및 JWT Refresh 인증 기능을 코드 리뷰한다.

이 문서는 Review 실행용 Workflow다. 공통 Review 규칙, Android 설계서, Architecture, API Contract와 실제 변경 코드를 비교하여 구현의 정확성과 안전성을 검증한다.

코드는 수정하지 않고 Review 의견과 결과 문서만 작성한다.

---

## 2. 작업 대상 프로젝트

```text
/Users/kimseongjin/Desktop/workspace/private-agent
```

위 프로젝트의 현재 미커밋 변경사항과 인증 기능에 직접 연결되는 기존 코드를 대상으로 한다.

---

## 3. 반드시 확인할 자료

다음 자료를 먼저 실제로 읽고 Review 근거로 사용한다.

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

### API Contract

```text
/Users/kimseongjin/Desktop/workspace/private-agent-backend/docs/backend-api-spec.xlsx
```

### Backend 실제 구현

```text
/Users/kimseongjin/Desktop/workspace/private-agent-backend
```

### iOS 참고 구현과 Review 결과

```text
/Users/kimseongjin/Desktop/workspace/ios/PrivateAgent
/Users/kimseongjin/Desktop/workspace/ios/PrivateAgent/docs/workflows/authentication-ios-review.md
/Users/kimseongjin/Desktop/workspace/ios/PrivateAgent/docs/reviews/authentication-ios-review.md
/Users/kimseongjin/Desktop/workspace/ios/PrivateAgent/docs/reviews/authentication-ios-rereview.md
```

iOS 자료는 사용자 동작과 인증 정책의 동등성을 확인하는 참고 자료로 사용한다. Swift 구조를 Android에 그대로 요구하지 말고 Kotlin, Retrofit, OkHttp 및 현재 Android Architecture에 적합한지 판단한다.

문서, API Contract와 Backend 코드가 다르면 Backend 실제 구현을 우선하고 차이를 Review 결과에 기록한다. 자료를 찾을 수 없으면 추측하지 말고 확인하지 못한 항목으로 기록한다.

---

## 4. Review 범위

먼저 `git status`, `git diff`, `git diff --cached`로 실제 변경 범위를 확인한다.

다음을 포함해 현재 Authentication Feature에서 변경된 파일과 직접 연결되는 코드만 검토한다.

- `PrivateAgentApplication`
- `AndroidManifest.xml`
- `LoginActivity`와 `LoginScreen`
- `AuthViewModel`과 인증 UI 상태
- `AuthRepository`
- `AgentApi`
- Login, Refresh, User 및 Error DTO
- `TokenStore`
- `AuthInterceptor`
- `TokenAuthenticator`
- `NetworkModule`
- Retrofit 및 OkHttp 구성
- Gradle 의존성 및 설정 변경
- 인증 관련 테스트 코드

이번 구현 범위는 다음과 같다.

- 로그인 API 연동
- Access Token과 Refresh Token의 안전한 쌍 저장
- 저장 완료 후 로그인 성공 상태 전환 및 기존 메인 화면 이동
- 인증이 필요한 요청에 Bearer Access Token 자동 적용
- 인증 제외 요청 처리
- Access Token 만료로 인한 401 처리
- 동시 401 발생 시 Refresh 단일 실행 및 결과 공유
- Refresh 성공 시 새 토큰 쌍 저장
- Refresh 후 원 요청 최대 한 번 재시도
- Refresh 실패 유형별 토큰 유지 또는 삭제
- 앱 재실행 시 저장된 인증 상태 확인 및 초기 화면 결정

다음은 현재 작업 범위에서 제외한다.

- 사용자가 직접 실행하는 로그아웃 UI 및 Logout API
- `/auth/me` 구현

`/api/plan`은 현재 정책상 인증 제외 요청이다. 설계서 또는 Backend 실제 구현과 충돌하면 임의로 변경하지 말고 충돌 사항을 기록한다.

---

## 5. 중점 Review 항목

### 5.1 로그인과 화면 상태

- 입력값 검증이 올바른가
- 로딩 중 중복 로그인 요청을 방지하는가
- 로그인 API 성공만으로 로그인 상태가 되지 않는가
- Access Token과 Refresh Token 저장까지 완료된 후에만 성공 상태로 전환하는가
- 토큰 저장 실패 시 기존 또는 부분 저장 토큰을 정리하는가
- 로그인 성공 후에만 Main 화면으로 이동하는가
- Login 화면이 Back Stack에 불필요하게 남지 않는가
- 서버 내부 오류나 민감한 오류 메시지를 사용자에게 그대로 노출하지 않는가

### 5.2 TokenStore

- Application Context만 사용하고 Activity Context를 보관하지 않는가
- Access Token과 Refresh Token을 하나의 논리적 쌍으로 저장하는가
- 저장 성공 여부를 확인할 수 있는가
- 부분 저장 또는 저장 실패 시 불일치가 남지 않는가
- 읽기 및 삭제 실패가 안전하게 처리되는가
- 일반 SharedPreferences에 평문으로 저장하지 않는가
- TokenStore 인스턴스가 불필요하게 중복 생성되지 않는가

### 5.3 Application과 NetworkModule 초기화

- `PrivateAgentApplication`이 AndroidManifest에 정확히 등록됐는가
- `NetworkModule.initialize(applicationContext)`가 `agentApi` 또는 TokenStore 최초 접근보다 먼저 실행되는가
- 초기화 순서에 따른 `UninitializedPropertyAccessException` 가능성이 없는가
- 동일한 TokenStore가 로그인, Interceptor 및 Authenticator에서 공유되는가
- 프로세스 재생성 및 앱 재실행 시 안전한가

### 5.4 인증 헤더

- Authorization 헤더 형식이 `Bearer {accessToken}`으로 정확한가
- 인증이 필요한 요청에만 Access Token을 추가하는가
- 기존 Authorization 헤더가 있을 때 정책이 명확한가
- 토큰이 없을 때 잘못된 헤더를 추가하지 않는가
- `/health`, `/auth/login`, `/auth/refresh`, `/api/plan` 제외 처리가 실제 URL 경로와 일치하는가
- 토큰 및 Authorization 헤더가 로그에 출력되지 않는가

### 5.5 Refresh와 동시성

- `agentApi`는 인증용 OkHttpClient를 실제로 사용하는가
- Refresh 호출용 API 인스턴스는 AuthInterceptor와 TokenAuthenticator가 없는 별도 OkHttpClient를 사용하는가
- Refresh 요청이 다시 TokenAuthenticator에 진입하는 순환이 없는가
- 여러 요청이 동시에 401을 받아도 Refresh가 실제로 한 번만 실행되는가
- 기다리던 요청이 갱신된 Access Token을 재사용하는가
- 동기화 영역에서 불필요한 다중 Refresh, 데이터 경쟁 또는 교착상태 가능성이 없는가
- Refresh 성공 후 새 토큰 쌍 저장까지 성공해야 원 요청을 재시도하는가
- 원 요청이 최대 한 번만 재시도되는가
- 재시도 요청이 다시 401이면 추가 Refresh 없이 중단되는가
- OkHttp `Authenticator`, Retrofit 동기 `Call.execute()`와 Thread 사용이 안전한가

### 5.6 Refresh 실패 분류

최소한 다음 실패를 구분하여 iOS 정책 및 Android 설계와 비교한다.

- Refresh Token 없음
- TokenStore 읽기 실패
- Refresh API 400
- Refresh API 401
- 기타 4xx
- 5xx 서버 오류
- 네트워크 연결 오류
- 타임아웃
- 응답 디코딩 오류 또는 빈 Body
- 새 토큰 쌍 저장 실패

각 실패에서 다음을 확인한다.

- 기존 토큰 유지 여부
- 토큰 삭제 여부
- 인증 상태 변경 여부
- 원 요청 재시도 여부
- 로그인 화면 복귀 여부
- 로그에 남기는 정보의 안전성

Refresh 400/401처럼 명시적으로 인증이 거절된 경우에는 토큰을 삭제하고 로그아웃 상태로 전환해야 한다. 네트워크 오류, 일시적인 서버 장애 또는 디코딩 오류에서는 기존 토큰을 즉시 삭제하지 않아야 한다. 새 토큰 쌍 저장 실패는 인증 성공으로 처리하지 않아야 한다.

### 5.7 앱 인증 상태 복원

- 앱 시작 시 저장된 Access Token과 Refresh Token 상태를 확인하는가
- 두 토큰 중 하나만 존재하면 불완전한 상태를 정리하는가
- 인증 상태 확인 전 잘못된 화면이 잠깐 노출되지 않는가
- 저장된 토큰이 있으면 Main 화면, 없으면 Login 화면으로 일관되게 진입하는가
- Refresh 거절 후 앱 전체 인증 상태와 화면이 Login 상태로 전환되는가

### 5.8 API Contract와 보안

- Endpoint와 HTTP Method가 Backend 실제 구현과 일치하는가
- Login 및 Refresh Request/Response 필드명과 타입이 일치하는가
- `AuthUser.id` 등 JSON 숫자와 Kotlin 타입이 일치하는가
- Backend 에러 응답 구조 및 HTTP Status를 올바르게 처리하는가
- 비밀번호, Access Token, Refresh Token, 전체 Request 및 Authorization 헤더를 로그에 남기지 않는가
- Base URL과 Secret을 소스에 새로 하드코딩하지 않았는가

### 5.9 Architecture와 코드 품질

- 기존 `UI → ViewModel → Repository → AgentApi` 구조를 불필요하게 훼손하지 않았는가
- UI, ViewModel, Repository, TokenStore, Interceptor 및 Authenticator의 책임이 적절한가
- 인증 갱신 책임이 ViewModel에 남아 있지 않은가
- 중복 코드, 사용하지 않는 import, 중복 객체 또는 죽은 코드가 없는가
- Kotlin 및 프로젝트 Coding Convention을 따르는가
- 기존 비인증 기능에 불필요한 영향을 주지 않는가

---

## 6. 빌드 및 테스트

코드를 수정하지 않은 상태에서 가능한 범위의 빌드와 기존 테스트를 실행한다.

최소한 다음을 확인한다.

```text
./gradlew test
./gradlew assembleDebug
```

명령이 프로젝트 구조와 다르면 실제 Gradle Wrapper 위치를 확인해 적절한 동등 명령을 실행하고 결과를 기록한다.

다음 런타임 시나리오의 검증 여부도 기록한다.

- 정상 로그인 및 Main 화면 이동
- 빈 입력과 잘못된 비밀번호
- 로그인 버튼 연속 클릭
- 앱 재실행 시 인증 상태 복원
- 만료된 Access Token으로 요청 후 Refresh 성공
- 동시 401 발생 시 Refresh 한 번 실행
- Refresh 400/401 시 토큰 삭제 및 Login 상태 전환
- Refresh 네트워크 오류 시 기존 토큰 유지
- Refresh 후 재시도 요청이 다시 401일 때 중단

직접 검증하지 못한 항목은 성공했다고 추정하지 말고, 검증하지 못한 이유와 필요한 검증 방법을 기록한다.

---

## 7. 결과 분류

Review 결과를 다음 등급으로 분류한다.

- **Critical**: 빌드 불가, 보안 문제, 데이터 손상 또는 심각한 인증 우회 가능성
- **Major**: 실제 동작 오류, 토큰 상태 불일치, 무한 Refresh/재시도, 동시성 또는 인증 상태 전환 문제
- **Minor**: 현재 기능을 막지는 않지만 개선이 필요한 문제
- **Good**: 코드 근거로 정상임을 확인한 항목
- **Summary**: 설계·API Contract 일치 여부, Architecture, 보안, 빌드 및 최종 판정

각 Issue에는 반드시 다음을 포함한다.

1. 파일과 위치
2. 문제 코드 또는 코드 근거
3. 발생 가능한 실제 시나리오
4. 설계 또는 API Contract와의 차이
5. 권장 수정 방향
6. 커밋 전 수정 필요 여부

이미 정상임이 확인된 동작을 추측으로 Issue 처리하지 않는다. 코드 근거가 부족한 내용은 확인 필요 또는 런타임 미검증 항목으로 분리한다.

---

## 8. 작업 제한

다음 작업은 하지 않는다.

- Android 코드 수정
- 테스트 코드 추가 또는 수정
- Refactoring
- API 변경
- Architecture 변경
- 기존 문서 수정
- Git commit
- Git push

Review 결과 Markdown 문서 작성 외에는 파일을 변경하지 않는다.

---

## 9. Review 결과 저장

리뷰 결과를 채팅으로만 보고하지 말고 다음 경로에 Markdown 문서로 저장한다.

```text
/Users/kimseongjin/Desktop/workspace/private-agent/docs/reviews/authentication-android-review.md
```

문서에는 다음 내용을 포함한다.

1. 리뷰 일시와 범위
2. 참고한 문서와 실제 코드
3. git 변경 범위
4. Critical/Major/Minor/Good
5. 각 Issue의 코드 근거와 시나리오
6. 권장 수정 방향과 커밋 전 수정 필요 여부
7. 빌드 및 테스트 결과
8. 런타임에서 검증하지 못한 항목과 이유
9. 설계·API Contract·Backend·iOS와 실제 구현의 차이
10. 최종 판정

Issue가 없더라도 빈 문서를 만들지 말고 검토한 항목과 Issue가 없다고 판단한 코드 근거를 기록한다.

최종 판정에는 다음을 명시한다.

- 현재 상태로 커밋 가능한가
- Critical/Major 수정이 필요한가
- 재리뷰가 필요한가
- 앱 인증 상태 복원까지 구현됐는가
- 다음 Android 기능으로 진행 가능한가

---

## 10. 채팅 완료 보고

채팅에는 다음만 간단히 요약한다.

- 저장한 Review 결과 문서 경로
- Critical/Major/Minor 개수
- 빌드 및 테스트 결과
- 커밋 가능 여부
- 재리뷰 필요 여부
- 다음 작업 진행 가능 여부

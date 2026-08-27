현재 Android 프로젝트에 로그인 및 JWT Refresh 인증 기능을 구현하기 위한 설계서를 작성해줘.

이번 작업은 설계와 문서 작성만 수행한다. Android 코드, Gradle 설정, 테스트 파일 및 기존 문서는 수정하지 않는다.

## 작업 대상 프로젝트

```text
/Users/kimseongjin/Desktop/workspace/private-agent
```

분석과 문서 작성은 위 프로젝트를 기준으로 수행한다.

## 작업 목적

기존 로그인 화면에 백엔드 로그인 API를 연결하고, 로그인 성공 후 기존 메인 화면으로 이동하는 Android 인증 기능을 설계한다.

로그인뿐 아니라 다음 인증 수명주기 전체를 포함해야 한다.

- 로그인
- Access Token과 Refresh Token 저장
- 인증 헤더 자동 적용
- Access Token 만료 시 Refresh
- 동시 401 요청 처리
- Refresh 성공 후 원 요청 재시도
- Refresh 실패 유형별 처리
- 앱 재실행 시 인증 상태 복원
- 로그아웃 및 토큰 삭제
- 로그인 성공 후 기존 메인 화면 전환

이 설계서는 이후 Android Feature 구현의 단일 기준 문서로 사용한다.

## 먼저 확인할 자료

다음 자료를 실제로 읽고 근거로 사용한다.

### Android 자료

- Android 프로젝트:
  /Users/kimseongjin/Desktop/workspace/private-agent/android

- Android 프로젝트 분석 문서:
  /Users/kimseongjin/Desktop/workspace/private-agent/docs/architecture/android-project-overview.md

- 현재 Android 프로젝트의 로그인 화면
- 앱 시작점과 기존 메인 화면
- ViewModel 및 UI 상태 처리 코드
- `NetworkModule`
- `AgentApi`
- Repository 계층
- Retrofit 및 OkHttp 구성
- AndroidManifest와 Gradle 설정
- 현재 로컬 저장소 관련 코드
- 관련 테스트 코드

### iOS 자료

- iOS 프로젝트:
  /Users/kimseongjin/Desktop/workspace/ios/PrivateAgent

- iOS 프로젝트 분석 문서:
  /Users/kimseongjin/Desktop/workspace/ios/PrivateAgent/docs/architecture/ios-project-overview.md

- iOS 로그인 인증 설계서:
  /Users/kimseongjin/Desktop/workspace/ios/PrivateAgent/docs/designs/login-auth.md

- iOS 인증 리뷰 문서:
  /Users/kimseongjin/Desktop/workspace/ios/PrivateAgent/docs/reviews/authentication-ios-review.md

- iOS 인증 재리뷰 문서:
  /Users/kimseongjin/Desktop/workspace/ios/PrivateAgent/docs/reviews/authentication-ios-rereview.md

* 로그인, Keychain, 인증 헤더, Refresh, 인증 상태 및 화면 전환 관련 파일

iOS 자료의 실제 경로나 이름이 다르면 프로젝트 내부에서 관련 문서를 찾아 정확한 경로를 기록한다.

iOS 구현은 사용자 동작과 인증 정책을 확인하기 위한 참고 자료로 사용한다. Swift의 타입이나 구조를 Android에 그대로 복사하지 말고, 현재 Android 프로젝트의 구조와 Kotlin 관례에 맞게 설계한다.

### 백엔드 자료

- API Spec 문서:
  /Users/kimseongjin/Desktop/workspace/private-agent-backend/docs/api-spec.md

- 백엔드 프로젝트:
  /Users/kimseongjin/Desktop/workspace/private-agent-backend

백엔드의 실제 인증 코드를 확인하여 다음 규격을 확정한다.

- 로그인 Endpoint와 HTTP Method
- 로그인 요청 필드
- 로그인 성공 응답
- 로그인 실패 응답
- Access Token과 Refresh Token 필드명
- Refresh Endpoint와 HTTP Method
- Refresh Token 전달 방식
- Refresh 성공 응답
- Refresh 실패 응답과 HTTP 상태 코드
- Access Token이 필요한 API
- 토큰 만료 및 폐기 정책
- Refresh Token Rotation 여부
- 로그아웃 Endpoint 존재 여부
- 백엔드가 실제로 반환하는 에러 형식

API 명세서와 백엔드 코드가 다르면 백엔드 실제 코드를 최우선으로 판단하고, 차이를 문서에 기록한다.

백엔드 또는 iOS 자료가 현재 작업 환경에서 확인되지 않으면 추측하지 말고 `확인 필요`로 표시한다.

## 현재 Android 구조 전제

기존 분석 결과는 다음과 같다. 실제 코드를 다시 확인하고 사실 여부를 검증한다.

- 단일 모듈 Jetpack Compose 앱
- `UI → ViewModel → Repository → AgentApi(Retrofit)` 구조
- `mutableStateOf`와 `StateFlow` 사용
- 별도의 DI 프레임워크 없음
- `NetworkModule` 싱글턴을 직접 참조
- 로그인 화면은 있지만 현재 인증 API 호출은 없음
- 현재 로그인 버튼은 인증 없이 `MainActivity`로 이동
- 인증, 토큰 저장, 인증 헤더 및 401 처리 관련 구현은 없음
- Base URL 환경 분리가 부족함
- 요청 객체를 로그로 출력하는 패턴이 존재함

현재 코드와 다르면 실제 코드를 기준으로 문서에 정정한다.

## 반드시 확정할 인증 정책

iOS 구현과 백엔드 규격을 근거로 아래 정책을 Android 설계에 반영한다.

1. Access Token과 Refresh Token이 모두 저장된 뒤에만 로그인 상태로 전환한다.
2. 토큰 쌍 저장 중 하나라도 실패하면 부분 저장 상태가 남지 않도록 롤백한다.
3. 인증이 필요한 요청에만 Access Token을 적용한다.
4. 로그인 및 Refresh 요청에는 기존 Access Token을 자동 적용하지 않는다.
5. 여러 요청에서 동시에 401이 발생해도 Refresh는 한 번만 실행한다.
6. Refresh 중 들어온 다른 요청은 동일한 Refresh 결과를 공유한다.
7. Refresh 성공 후 원 요청은 최대 한 번만 재시도한다.
8. 무한 401 및 무한 Refresh 반복을 방지한다.
9. Refresh가 400 또는 401 등 명시적으로 거절되면 토큰을 삭제하고 로그아웃 상태로 전환한다.
10. Refresh 중 네트워크 오류나 일시적인 서버 장애가 발생하면 기존 토큰을 즉시 삭제하지 않는다.
11. Refresh 성공으로 새 토큰 쌍을 저장할 때도 원자성 또는 롤백을 보장한다.
12. 앱 재실행 시 저장된 토큰 상태를 확인한 뒤 초기 화면을 결정한다.
13. 로그아웃 시 토큰 삭제와 앱 인증 상태 변경의 순서를 명확히 정의한다.
14. 비밀번호, Access Token, Refresh Token 및 인증 헤더는 로그에 출력하지 않는다.
15. 로그인 성공 전에는 기존 메인 화면으로 이동하지 않는다.

백엔드 또는 iOS의 실제 정책과 위 내용이 충돌한다면 임의로 결정하지 말고 충돌 내용을 별도로 기록한다.

## 설계 시 판단할 내용

### 1. 인증 모델과 API

다음을 설계한다.

- 로그인 요청 및 응답 DTO
- Refresh 요청 및 응답 DTO
- 백엔드 에러 응답 DTO
- Domain Model 분리 필요 여부
- 인증 전용 Retrofit Service 분리 여부
- 기존 `AgentApi`를 확장할지 별도 `AuthApi`를 만들지
- 로그인, Refresh, 로그아웃 API 호출 책임

각 선택은 현재 프로젝트 구조에 맞는 이유와 함께 설명한다.

### 2. TokenStore

다음을 결정한다.

- Android에서 토큰을 저장할 기술
- DataStore, EncryptedSharedPreferences, Android Keystore 등의 적용 방식
- Access Token과 Refresh Token을 하나의 논리적 토큰 쌍으로 다루는 방법
- 저장 성공과 부분 실패를 판단하는 방법
- 부분 저장 실패 시 롤백 방법
- 토큰 읽기·저장·삭제 Interface
- 저장소 구현의 초기화 위치
- 저장 실패가 로그인 상태에 미치는 영향
- 테스트에서 Fake 또는 In-memory 구현으로 교체하는 방법

보안성과 현재 프로젝트의 복잡도를 함께 고려해 한 가지 권장안을 선택한다.

### 3. 인증 헤더

다음을 설계한다.

- OkHttp Interceptor 적용 위치
- 인증이 필요한 요청을 구분하는 방식
- 로그인 및 Refresh 요청을 제외하는 방식
- 토큰이 없을 때의 처리
- Authorization 헤더 형식
- 기존 헤더가 존재할 때의 처리
- 토큰과 인증 헤더가 로그에 노출되지 않도록 하는 방법

### 4. Refresh와 동시성

다음을 구체적으로 설계한다.

- 401을 처리할 구성요소
- OkHttp `Authenticator` 또는 Interceptor 중 무엇을 사용할지
- 선택한 이유와 현재 구조에서의 장단점
- 동시 401에서 Refresh 한 번만 실행하는 방법
- `Mutex`, 공유 `Deferred` 등 동시성 제어 수단
- 대기 중인 요청이 Refresh 결과를 공유하는 흐름
- Refresh 요청이 다시 인증 갱신 대상이 되지 않도록 하는 방법
- Refresh 성공 후 원 요청을 한 번만 재시도하는 방법
- 이미 한 번 재시도한 요청을 식별하는 방법
- 무한 루프 방지 조건
- Coroutine과 OkHttp 동기 호출 경계에서의 주의점
- Refresh 중 앱이 로그아웃되거나 프로세스가 종료되는 경우의 처리

### 5. Refresh 실패 분류

최소한 다음 실패를 구분한다.

- Refresh Token 없음
- Refresh Token 저장소 읽기 실패
- Refresh API의 400 응답
- Refresh API의 401 응답
- 기타 4xx 응답
- 5xx 서버 오류
- 네트워크 연결 오류
- 타임아웃
- 응답 디코딩 오류
- 새 토큰 쌍 저장 실패

각 경우에 대해 다음을 표로 정리한다.

- 기존 토큰 유지 여부
- 토큰 삭제 여부
- 인증 상태 변경 여부
- 원 요청 재시도 여부
- 사용자에게 표시할 동작
- 로그에 남길 수 있는 정보

### 6. 앱 전체 인증 상태

다음을 설계한다.

- 로그인 여부를 앱 전체에서 공유할 객체
- `AuthSession`, `SessionManager`, `AuthState` 등 권장 타입과 책임
- `StateFlow`를 사용할지 여부
- 저장된 토큰 상태와 메모리 인증 상태의 관계
- 앱 시작 시 인증 상태 초기화 순서
- 초기 상태 확인 중 표시할 화면
- 로그인 성공 상태 전환
- Refresh 거절 후 로그아웃 상태 전환
- 사용자가 직접 로그아웃하는 흐름
- 인증 상태와 Activity/Navigation의 결합도를 낮추는 방법

### 7. 화면 전환

현재 로그인 화면과 메인 화면 구조를 근거로 다음을 설계한다.

- 로그인 버튼 입력 검증
- 로그인 요청 중 중복 제출 방지
- 로딩 상태
- 로그인 실패 메시지
- 로그인 성공 판정 조건
- 토큰 저장 완료 후 인증 상태 변경
- 인증 상태 변경 후 기존 메인 화면 이동
- 로그인 화면이 Back Stack에 남지 않도록 하는 방법
- 앱 재실행 시 로그인 화면과 메인 화면을 결정하는 방법
- Refresh 거절로 로그아웃될 때 로그인 화면으로 복귀하는 방법

현재 Activity 분리 구조를 유지할지 Navigation Compose 인증 그래프로 전환할지도 비교한다. 이번 Feature 범위에서 가장 자연스럽고 변경 범위가 작은 권장안을 하나 선택한다.

### 8. 의존성 구성

현재 DI 프레임워크가 없다는 점을 고려해 다음을 비교한다.

- 기존 `NetworkModule` 싱글턴 확장
- Custom `Application`을 Composition Root로 사용하는 수동 DI
- Hilt 도입

이번 인증 Feature에서 권장하는 방식을 하나 선택하고 다음을 설명한다.

- 선택 이유
- 객체 생성 및 생명주기
- 의존성 전달 방식
- 테스트 대체 가능성
- 향후 Feature 확장성
- 이번 작업에서 수정될 범위

필요 이상의 대규모 아키텍처 변경은 권장하지 않는다.

### 9. 에러와 로그

다음을 설계한다.

- 네트워크 오류를 Repository 또는 ViewModel 상태로 변환하는 방식
- 사용자 표시용 메시지와 내부 오류의 분리
- 로그인 실패와 시스템 장애의 구분
- 디버그 및 릴리스 로그 정책
- 비밀번호와 토큰 마스킹 또는 로그 제외
- 기존 요청 객체 전체 출력 코드의 처리 방향
- 서버 응답 Body에 민감정보가 포함될 가능성

### 10. 테스트 전략

다음을 포함한다.

- 로그인 성공
- 로그인 실패
- 토큰 두 개 정상 저장
- 토큰 쌍 부분 저장 실패와 롤백
- 인증 헤더 추가
- 인증 제외 API의 헤더 미적용
- 단일 401 Refresh
- 동시 401에서 Refresh 한 번만 호출
- Refresh 성공 후 원 요청 한 번 재시도
- 반복 401 무한 루프 방지
- Refresh 400/401 시 토큰 삭제와 로그아웃
- Refresh 네트워크 오류 시 토큰 유지
- 새 토큰 저장 실패 처리
- 앱 재실행 시 인증 상태 복원
- 로그인 성공 후 메인 화면 이동
- 로그아웃 후 로그인 화면 복귀

현재 프로젝트에 테스트 기반이 부족하다면 필요한 최소 테스트 도구와 설정도 설계에 포함하되 실제 설정 파일은 수정하지 않는다.

## iOS와 Android 비교

iOS의 실제 인증 구성요소와 Android 권장 구성요소를 표로 정리한다.

예:

| 책임 | iOS 실제 구성요소 | Android 권장 구성요소 | 동일하게 유지할 정책 | 플랫폼별 차이 |
| ---- | ----------------- | --------------------- | -------------------- | ------------- |

최소한 다음 항목을 비교한다.

- 로그인 API
- 인증 Repository
- Keychain과 Android 보안 저장소
- 인증 헤더 적용
- 401 처리
- 동시 Refresh 제어
- 인증 상태 공유
- 앱 시작 시 토큰 복원
- 로그인 성공 후 메인 화면 전환
- 로그아웃
- 테스트

iOS 타입명과 실제 파일 경로를 확인해 기록한다.

## 예상 변경 파일

실제 Android 구조를 기준으로 다음을 구분해 표로 작성한다.

- 새로 추가할 것으로 예상되는 파일
- 수정할 것으로 예상되는 기존 파일
- 그대로 재사용할 파일
- 검토만 하고 수정하지 않을 파일

각 파일에 대해 다음을 기록한다.

- 예상 경로
- 타입 또는 파일명
- 역할
- 주요 의존성
- 변경 이유
- 구현 단계

존재하지 않는 파일 경로를 확정된 사실처럼 작성하지 말고 `예상 경로`임을 표시한다.

## 단계별 구현 계획

실제 구현을 다음과 같이 작고 검증 가능한 단계로 나눈다.

1. 백엔드 인증 규격과 Android 모델 확정
2. 인증 API 및 DTO 구성
3. TokenStore 구현
4. AuthRepository 구현
5. 로그인 ViewModel 및 UI 연동
6. 로그인 성공 후 메인 화면 전환
7. 인증 헤더 Interceptor 적용
8. 단일 Refresh 구현
9. 동시 401 및 Refresh 공유 처리
10. Refresh 실패와 로그아웃 연결
11. 앱 시작 시 인증 상태 복원
12. 로그아웃 구현
13. 단위 테스트 및 통합 테스트
14. 전체 빌드와 회귀 검증
15. 보안 로그 점검

각 단계마다 다음을 작성한다.

- 목적
- 선행 조건
- 추가 또는 수정할 파일
- 구현해야 할 책임
- 완료 기준
- 검증 방법
- 주의할 위험
- iOS와 맞춰야 할 동작

완성된 Kotlin 코드를 작성하지 않는다. 타입 관계나 흐름 설명에 필요한 짧은 인터페이스 형태 또는 의사코드는 허용하지만, 복사해서 바로 사용할 수 있는 전체 구현 코드는 작성하지 않는다.

## 문서 형식

다음 순서로 작성한다.

1. 설계 목적과 범위
2. 확인한 자료와 근거
3. 백엔드 실제 인증 API 규격
4. iOS 인증 구현 요약
5. Android 현재 구조와 인증 연결 지점
6. Android 인증 아키텍처
7. 인증 데이터 모델과 API
8. TokenStore 및 보안 저장소
9. 인증 헤더 처리
10. 401 및 Refresh 처리
11. 동시 Refresh 제어
12. 인증 상태 관리
13. 앱 시작 및 화면 전환 흐름
14. 오류 분류와 처리 정책
15. 로그 및 보안 정책
16. iOS와 Android 대응 관계
17. 예상 신규 파일
18. 예상 수정 파일
19. 테스트 전략
20. 단계별 구현 순서와 완료 기준
21. 위험 요소와 대응 방안
22. 구현 전에 확정해야 할 사항
23. 최종 권장 설계 요약

중요한 판단에는 반드시 근거가 되는 실제 파일 경로와 타입 또는 함수명을 기록한다.

인증 처리 흐름은 필요하다면 Mermaid Sequence Diagram으로 표현한다. 최소한 다음 흐름이 명확해야 한다.

- 로그인 성공과 토큰 저장
- 인증 요청과 401
- 동시 요청에서 단일 Refresh
- Refresh 성공 후 재시도
- Refresh 거절 후 로그아웃
- 앱 시작 시 인증 상태 복원

## 결과 문서

설계 결과를 다음 경로에 저장한다.

```text
/Users/kimseongjin/Desktop/workspace/private-agent/docs/designs/android-login-auth.md
```

상위 디렉터리가 없다면 생성한다.

## 작업 제한

- Android 기능을 구현하지 않는다.
- Kotlin 코드와 Gradle 설정을 수정하지 않는다.
- iOS와 백엔드 코드를 수정하지 않는다.
- 기존 문서를 수정하지 않는다.
- 결과 설계서 이외의 파일을 생성하지 않는다.
- 현재 사용자 변경사항을 정리하거나 되돌리지 않는다.
- 민감정보의 실제 값을 문서나 대화에 출력하지 않는다.
- Git commit과 push를 수행하지 않는다.
- 근거 없이 구조나 API 규격을 추측하지 않는다.
- 관련 없는 파일을 광범위하게 분석하지 않는다.

작업 시작 전 `git status`를 확인하되 기존 변경사항은 수정하지 않는다.

작업 완료 후 다시 `git status`를 확인하여 결과 문서 한 개 외에 변경된 파일이 없는지 검증한다.

## 완료 보고

작업 완료 후 대화에는 다음만 간단히 보고한다.

1. 생성한 설계서 경로
2. 확인한 Android·iOS·백엔드 자료
3. 최종 권장 Android 인증 구조
4. 확정한 백엔드 로그인 및 Refresh 규격
5. iOS와 동일하게 유지할 핵심 인증 정책
6. 예상되는 신규 파일과 수정 파일 수
7. 구현 전에 추가 확인이 필요한 사항
8. 변경된 파일이 설계서 한 개뿐인지
9. Android Feature 구현을 시작할 수 있는 상태인지

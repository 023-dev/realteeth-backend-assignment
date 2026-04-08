# Section 6. 실행 가능 조건

이 문서는 과제 안내서의 `6. 실행 가능 조건` 충족 여부를 확인하기 위한 실행 안내서입니다.

## 1) 상용 계정/자격 증명 필요 여부
- Mock Worker 연동에 필요한 API key는 공개된 발급 엔드포인트를 통해 스크립트로 발급합니다.
  - 스크립트: `./scripts/setup-env.sh "<candidateName>" "<email>"`
  - 결과: `.env`의 `MOCK_WORKER_API_KEY` 자동 설정

## 2) 컨테이너 환경 실행 가능
- `Dockerfile`과 `compose.yaml`을 제공합니다.
- 기본 실행:

```bash
./scripts/setup-env.sh "이름" "이메일"
docker compose up --build
```

## 3) 실행 방법/포트 정보
- 실행 명령은 README와 동일합니다.
- 기본 포트:
  - API: `8080`
  - MySQL: `3306`

## 4) 로컬 인프라 구성
- `docker compose` 기준 로컬에서 바로 실행되는 컴포넌트:
  - `app` (Spring Boot)
  - `mysql` (MySQL 8.4)
- Mock Worker는 로컬 compose에 포함하지 않고 기본 외부 엔드포인트(`https://dev.realteeth.ai/mock`)를 사용합니다.

## 5) 검증 명령
```bash
./gradlew test
./gradlew build
```

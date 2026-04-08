# RealTeeth API 과제 설계 설명

## 개요
- 본 시스템은 이미지 처리 요청을 받아 작업을 저장하고, 외부 Mock Worker와 비동기 polling 방식으로 연동한다.
- API는 `POST /api/v1/jobs`, `GET /api/v1/jobs/{jobId}`, `GET /api/v1/jobs` 세 개를 제공한다.
- `POST` 응답은 요약 필드(`jobId`, `status`, `imageUrl`, `createdAt`)를 반환한다.
- `GET /{jobId}` 응답은 상세 필드(`jobId`, `status`, `imageUrl`, `result`, `errorMessage`, `retryCount`, `createdAt`, `updatedAt`)를 반환한다.
- `GET /api/v1/jobs` 기본 정렬은 `createdAt DESC, id DESC`를 사용한다.
- 설계 목표는 과제 요구사항을 충족하면서도, 문서와 코드가 같은 설명을 하도록 구조를 단순하게 유지하는 것이다.

## 설계 선택 기준
- 이번 구현에서는 과제 필수 요구사항이 아닌 요소는 과감히 제외했다.
- 제외한 항목:
  - Redis cache
  - distributed lock / named lock
  - MQ / outbox
  - 즉시 submit 경로
- 이유:
  - 중복 요청 처리, 상태 전이, 처리 보장 모델, 재시작 복구, 정합성 설명은 DB + scheduler + lease 만으로 충분히 설명 가능하다.
  - 과제 범위에서는 성능 최적화보다 설명 가능성과 일관성이 더 중요하다.

## 상태 모델 설계 의도
- 상태는 `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED` 네 개만 사용한다.
- 의미:
  - `PENDING`: 요청은 저장됐지만 외부 Worker 전송 전이거나 재시도 대기 중인 상태
  - `PROCESSING`: Worker가 작업을 받아 처리 중인 상태
  - `COMPLETED`: 최종 성공
  - `FAILED`: 최종 실패
- 허용 전이:
  - `PENDING -> PROCESSING`
  - `PENDING -> FAILED`
  - `PROCESSING -> COMPLETED`
  - `PROCESSING -> FAILED`
- 금지 전이:
  - terminal 상태(`COMPLETED`, `FAILED`) 이후의 모든 전이
  - `PROCESSING -> PENDING` 같은 역전이
- 상태 전이 검증은 `Job` 도메인 엔티티가 담당한다.

## 중복 요청 처리 전략
- 중복 요청은 `X-Idempotency-Key` 기준으로 처리한다.
- DB `idempotency_key` unique 제약을 최종 source of truth로 사용한다.
- 처리 방식:
  - 같은 key + 같은 정규화 `imageUrl` -> 기존 작업 반환 (`200 OK`)
  - 같은 key + 다른 정규화 `imageUrl` -> `409 Conflict`
  - key가 없으면 신규 작업으로 처리 (`202 Accepted`)
- 정규화 규칙은 `trim -> URI.normalize().toString()` 이다.
- Redis 같은 별도 캐시는 두지 않았다. 과제 범위에서는 DB unique + 조회만으로 충분하고, 설명도 더 단순하다.

## 생성 요청 처리 흐름
- `POST /jobs`는 외부 Worker를 즉시 호출하지 않는다.
- 요청이 들어오면:
  1. 멱등성 검사
  2. 신규면 `PENDING` 저장
  3. `202 Accepted` 반환
- 이후 Pending 스케줄러가 외부 Worker submit을 담당한다.
- 이 구조를 선택한 이유:
  - 생성 API 응답 시간을 짧게 유지할 수 있다.
  - 외부 장애를 생성 요청 지연과 분리할 수 있다.
  - 재시작 복구와 처리 보장 모델을 더 직접적으로 설명할 수 있다.

## 실패 처리 전략
- `POST /jobs`는 작업을 `PENDING`으로 저장만 하고 바로 응답한다.
- 실제 외부 연동은 백그라운드 스케줄러가 수행한다.
- 실패 분류:
  - 명확 실패: 잘못된 응답, poll `jobId` 불일치, 설정 오류, 계약 위반 -> `FAILED`
  - 불확실 실패: timeout, connect failure, server error, rate limit(429), circuit open -> `PENDING + nextAttemptAt`
- `nextAttemptAt`은 지수 backoff를 적용한 다음 시도 시각이다.

## API 에러 응답 정책
- 공통 에러 응답 형식은 `{ code, message, timestamp }`를 유지한다.
- 요청 본문 파싱 실패(malformed JSON)는 `400 Bad Request` + `VALIDATION_FAILED`로 처리한다.
- 지원하지 않는 `Content-Type` 요청도 `400 Bad Request` + `VALIDATION_FAILED`로 처리한다.
- 백그라운드 처리 중 예상 외 내부 예외는 `errorMessage`에 원문 예외를 노출하지 않고 일반화된 문구를 저장한다(상세 원인은 서버 로그로 확인).

## 처리 보장 모델
- 본 시스템의 처리 보장 모델은 `at-least-once`다.
- 근거:
  - `PENDING` 작업은 재시도와 recovery 대상으로 남는다.
  - 서버 재시작 후에도 미완료 작업을 다시 스캔해 처리한다.
  - 다만 외부 Worker 호출 성공 후 DB 상태 반영 전에 서버가 죽으면, 같은 작업이 다시 전송될 가능성이 있다.
- 즉, 유실 가능성은 줄이지만 정확히 한 번(`exactly-once`)은 보장하지 않는다.

## 서버 재시작 시 동작
- 기동 직후 `JobRecoveryRunner`가 미완료 작업 복구를 시작한다.
- 복구 대상:
  - `PENDING`: 다시 submit 시도
  - `PROCESSING`: Worker 상태 polling 재개
- `COMPLETED`, `FAILED`는 terminal이므로 복구 대상이 아니다.

## Graceful Shutdown
- `server.shutdown=graceful`을 적용해 종료 시 in-flight 요청/작업이 먼저 정리되도록 했다.
- `spring.lifecycle.timeout-per-shutdown-phase` 기본값은 `35s`이며, 환경변수 `APP_SHUTDOWN_TIMEOUT`으로 조정할 수 있다.
- 강제 종료(`SIGKILL`)는 graceful 대상이 아니므로 정합성 리스크를 완전히 제거하지는 못한다.

## 데이터 정합성이 깨질 수 있는 지점
- 외부 Worker submit 성공 후 DB를 `PROCESSING`으로 저장하기 전에 서버가 종료되는 경우
  - DB에는 여전히 `PENDING`으로 남고, recovery 후 다시 submit될 수 있다.
- Worker polling 결과가 `COMPLETED`였지만 DB 저장 전에 서버가 종료되는 경우
  - 이후 재조회 시 같은 외부 작업을 다시 확인할 수 있다.
- lease 만료 후 다른 실행 주체가 같은 job을 다시 선점하는 경우
  - 중복 처리를 완전히 제거하지는 못하지만, optimistic lock과 상태 체크로 위험을 줄인다.

## 동시 요청 발생 시 고려 사항
- 같은 멱등키로 동시에 생성 요청이 들어오면 DB unique 제약으로 최종 하나의 row만 남긴다.
- 스케줄러와 recovery runner가 동시에 같은 job을 집을 수 있는 상황은 `lease + optimistic lock`으로 흡수한다.
- `leaseOwner`, `leaseExpiresAt`은 API 상태와 무관한 내부 경합 제어용 필드다.

## 외부 시스템 연동 방식 및 선택 이유
- Mock Worker는 webhook이 없고 polling만 가능하므로, 서버 내부에서 주기적으로 상태를 확인하는 구조를 선택했다.
- 연동 방식:
  - Pending 스케줄러가 `POST /mock/process`
  - Processing 스케줄러가 `GET /mock/process/{jobId}`
- 외부 호출에는 수동 `CircuitBreaker`를 사용한다.
- 이유:
  - 연속 장애 시 외부 시스템을 과도하게 두드리지 않기 위해서다.

## 트래픽 증가 시 병목 가능 지점
- DB `idempotency_key` unique 인덱스 경합
- `findEligibleJobs` 기반 스케줄러 스캔 쿼리
- 외부 Mock Worker latency, timeout, 5xx 응답
- 긴 polling 구간에서 `PROCESSING` 작업이 많아질 때의 조회 부하

## 로컬 실행
```bash
./scripts/setup-env.sh "이름" "이메일"
docker compose up --build
```

- 본 과제 실행에는 별도의 상용 계정이나 유료 자격 증명이 필요하지 않습니다.
  - `scripts/setup-env.sh`가 Mock Worker 키 발급 API를 호출해 `.env`의 `MOCK_WORKER_API_KEY`를 채웁니다.

- 기본 포트:
  - API: `8080`
  - MySQL: `3306`
- 스케줄러 동시 실행 튜닝이 필요하면 `JOB_SCHEDULER_POOL_SIZE`로 `spring.task.scheduling.pool.size` 값을 조정할 수 있습니다.
- 운영 기본값은 `JOB_SCHEDULER_POOL_SIZE=2`를 권장합니다.
- `JOB_SCHEDULER_POOL_SIZE=4`는 아래 조건이 동시에 보일 때만 검토합니다.
  - 스케줄 작업 종류가 3개 이상으로 늘어남
  - 스케줄 시작 지연 또는 backlog가 지속적으로 증가함
  - DB 및 외부 Mock Worker 여유가 확인됨
- `job.scheduler.enabled=false`로 비활성화하면 `Pending/Processing` 스케줄러와 기동 직후 recovery runner가 모두 동작하지 않습니다.

## 테스트
```bash
./gradlew test --tests "com.realteeh.api.job.domain.JobStatusTest"
./gradlew test --tests "com.realteeh.api.job.presentation.JobV1ApiControllerTest"
./gradlew test
```

## 제출 패키지 문서
- 설계 설명 상세: `docs/submission/5-design-explanation.md`
- 실행 가능 조건 상세: `docs/submission/6-run-conditions.md`
- 제출 방법 체크리스트: `docs/submission/7-submission-method.md`

## 제출 방법
- GitHub Repository 링크를 제출합니다.
  - 제출 링크: `https://github.com/023-dev/realteeth-backend-assignment`
- 실행 방법과 설계 설명은 본 `README.md`에 포함합니다.
  - 설계 설명: 상태 모델, 실패 처리, 동시 요청 고려, 병목 지점, 외부 연동 방식/선택 이유
  - 실행 방법: `./scripts/setup-env.sh ...`, `docker compose up --build`, 포트 정보(`8080`, `3306`)

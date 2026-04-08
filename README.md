# RealTeeth API 과제 설계 설명

## 개요
- 이 서버는 이미지 처리 요청을 받으면 작업을 저장하고, 외부 Mock Worker에 처리를 맡긴 뒤 결과를 조회할 수 있게 합니다.
- 제공 API는 아래 3개입니다.
  - `POST /api/v1/jobs`
  - `GET /api/v1/jobs/{jobId}`
  - `GET /api/v1/jobs`
- 작업 생성 시에는 즉시 외부를 호출하지 않고, 먼저 `PENDING(대기)` 상태로 저장한 뒤 `202 Accepted`를 반환합니다.
- 이후 스케줄러가 외부 Worker 전송과 상태 조회를 담당합니다.

## 상태 모델 설계 의도
- 상태는 4개만 사용합니다.
  - `PENDING`(대기): 저장은 되었지만 외부 전송 전이거나 재시도 대기 중
  - `PROCESSING`(처리 중): 외부 Worker가 처리 중
  - `COMPLETED`(완료): 최종 성공
  - `FAILED`(실패): 최종 실패
- 허용 전이는 아래처럼 단순하게 제한했습니다.
  - `PENDING -> PROCESSING`
  - `PENDING -> FAILED`
  - `PROCESSING -> COMPLETED`
  - `PROCESSING -> FAILED`
- `COMPLETED`, `FAILED`는 종료 상태라서 이후 전이를 허용하지 않습니다.
- 상태 전이 검증은 도메인 객체(`Job`, `JobStatus`)가 담당합니다.

## 중복 요청 처리 전략
- 중복 방지 키(`X-Idempotency-Key`) 기준으로 중복 요청을 처리합니다.
- DB의 `idempotency_key` unique 제약을 최종 기준으로 사용합니다.
- 처리 규칙은 다음과 같습니다.
  - 같은 키 + 같은 정규화 `imageUrl`이면 기존 작업 반환 (`200 OK`)
  - 같은 키 + 다른 정규화 `imageUrl`이면 충돌 (`409 Conflict`)
  - 키가 없으면 신규 작업 생성 (`202 Accepted`)
- URL 비교는 `trim -> URI.normalize().toString()`으로 정규화한 값으로 수행합니다.

## 생성 요청 처리 흐름
- `POST /api/v1/jobs`는 외부 Worker를 즉시 호출하지 않습니다.
- 흐름은 아래와 같습니다.
  1. 입력 검증 및 멱등키 확인
  2. 신규 요청이면 `PENDING` 상태로 저장
  3. `202 Accepted` 응답
  4. 이후 Pending 스케줄러가 외부 Worker에 전송
- 이렇게 구성한 이유:
  - 생성 API 응답 시간을 짧게 유지할 수 있습니다.
  - 외부 장애를 생성 요청 지연과 분리할 수 있습니다.

## 실패 처리 전략
- 외부 연동 실패를 두 가지로 단순 분류합니다.
  - 명확 실패(즉시 실패 처리):
    - 응답 형식 오류
    - poll `jobId` 불일치
    - 설정 오류
    - 계약 위반
    - 처리: `FAILED`
  - 불확실 실패(재시도 대상):
    - timeout
    - connect failure
    - server error(5xx)
    - rate limit(429)
    - circuit open
    - 처리: `PENDING + nextAttemptAt`
- `nextAttemptAt`은 지수 backoff(재시도 간격 증가)로 계산합니다.

## 동시 요청 발생 시 고려 사항
- 같은 멱등키로 동시에 요청이 들어오면 DB unique 제약으로 최종 1건만 유지됩니다.
- 스케줄러와 recovery runner가 동시에 같은 작업을 잡는 상황은 아래 조합으로 흡수합니다.
  - lease(작업 선점 정보)
  - optimistic lock(낙관적 락)
- `leaseOwner`, `leaseExpiresAt`은 API 상태가 아니라 내부 경합 제어용 필드입니다.

## 외부 시스템 연동 방식 및 선택 이유
- Mock Worker는 webhook이 없고 polling 방식만 가능해 서버 내부 주기 조회 방식을 선택했습니다.
- 연동 방식:
  - Pending 스케줄러: `POST /mock/process`
  - Processing 스케줄러: `GET /mock/process/{jobId}`
- 외부 호출은 수동 `CircuitBreaker`로 보호합니다.
- 선택 이유:
  - 연속 장애 시 외부 시스템 과호출을 줄일 수 있습니다.
  - 실패 분류와 재시도 정책을 단순하게 유지할 수 있습니다.

## 트래픽 증가 시 병목 가능 지점
- DB `idempotency_key` unique 인덱스 경합
- `findEligibleJobs` 기반 스케줄러 스캔 쿼리 부하
- 외부 Mock Worker의 지연/타임아웃/5xx
- `PROCESSING` 작업 누적 시 poll 조회 부하

## 처리 보장 모델
- 본 시스템은 `at-least-once`(최소 1회 처리 보장) 모델입니다.
- 이유:
  - `PENDING` 작업은 재시도/복구 대상으로 남습니다.
  - 재시작 후 미완료 작업을 다시 스캔합니다.
  - 단, 외부 전송 성공 후 DB 반영 전에 장애가 나면 중복 전송 가능성이 있습니다.
- 즉, 유실 가능성은 줄이지만 `exactly-once`는 보장하지 않습니다.

## 서버 재시작 시 동작
- 앱 시작 직후 `JobRecoveryRunner`가 미완료 작업을 복구합니다.
  - `PENDING`: 재전송 시도
  - `PROCESSING`: 상태 조회 재개
- `COMPLETED`, `FAILED`는 복구 대상이 아닙니다.

## 데이터 정합성이 깨질 수 있는 지점
- 외부 submit 성공 후 DB를 `PROCESSING`으로 저장하기 전 서버 종료
  - DB에는 `PENDING`으로 남아 재전송될 수 있습니다.
- poll 결과가 `COMPLETED`여도 DB 저장 전 서버 종료
  - 이후 동일 외부 작업을 다시 조회할 수 있습니다.
- lease 만료 후 다른 실행 주체가 같은 작업 재선점
  - 완전한 중복 제거는 못 하지만 optimistic lock과 상태 검증으로 위험을 줄입니다.

## Graceful Shutdown
- `server.shutdown=graceful`을 적용했습니다.
- `spring.lifecycle.timeout-per-shutdown-phase` 기본값은 `35s`이며 `APP_SHUTDOWN_TIMEOUT`으로 조정할 수 있습니다.
- 강제 종료(`SIGKILL`)까지는 완전히 방어할 수 없습니다.

## 로컬 실행 (요구사항 6)
```bash
./scripts/setup-env.sh "이름" "이메일"
docker compose up --build
```

- `setup-env.sh`가 Mock Worker 키 발급 API를 호출해 `.env`의 `MOCK_WORKER_API_KEY`를 채웁니다.
- 컨테이너 구성:
  - `app` (Spring Boot)
  - `mysql` (MySQL 8.4)
- 기본 포트:
  - API: `8080`
  - MySQL: `3306`
- 스케줄러 튜닝:
  - `JOB_SCHEDULER_POOL_SIZE`로 스케줄러 풀 크기를 조정할 수 있습니다.
  - 기본 권장값은 `2`입니다.
- `job.scheduler.enabled=false`이면 Pending/Processing 스케줄러와 startup recovery runner가 모두 비활성화됩니다.

## 테스트
```bash
./gradlew test --tests "com.realteeh.api.job.domain.JobStatusTest"
./gradlew test --tests "com.realteeh.api.job.presentation.JobV1ApiControllerTest"
./gradlew test
```

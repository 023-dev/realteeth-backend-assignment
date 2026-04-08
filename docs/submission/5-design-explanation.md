# 설계 설명 문서

이 문서는 과제 안내서의 `5. 설계 설명 문서 제출` 항목을 빠르게 검토할 수 있도록 README 핵심 내용을 별도로 정리한 보조 문서입니다.

## 상태 모델 설계 의도
- 상태는 `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED` 네 개만 사용합니다.
- 허용 전이는 아래로 제한합니다.
  - `PENDING -> PROCESSING`
  - `PENDING -> FAILED`
  - `PROCESSING -> COMPLETED`
  - `PROCESSING -> FAILED`
- `COMPLETED`, `FAILED`는 terminal 상태이며 추가 전이를 허용하지 않습니다.
- 상태 전이 검증 책임은 도메인(`Job`, `JobStatus`)이 가집니다.

## 실패 처리 전략
- 생성 API(`POST /api/v1/jobs`)는 외부 Worker를 즉시 호출하지 않고 `PENDING` 저장 후 `202`를 반환합니다.
- 실패는 두 그룹으로 분류합니다.
  - 명확 실패: 계약 위반/응답 무결성 오류/설정 오류 -> `FAILED`
  - 불확실 실패: timeout/connect failure/server error/429/circuit open -> `PENDING + backoff`
- 재시도는 `nextAttemptAt`과 지수 backoff로 제어합니다.

## 동시 요청 발생 시 고려 사항
- 같은 `X-Idempotency-Key` 동시 요청은 DB `idempotency_key` unique 제약으로 최종 1건만 유지합니다.
- 같은 멱등키 + 같은 payload는 기존 작업 `200`을 반환하고, 다른 payload는 `409`를 반환합니다.
- 스케줄러와 recovery 경합은 `lease + optimistic lock`으로 흡수합니다.

## 트래픽 증가 시 병목 가능 지점
- `idempotency_key` unique 인덱스 경합
- `findEligibleJobs` 기반 스케줄러 스캔 쿼리 부하
- 외부 Mock Worker latency/timeout/5xx
- `PROCESSING` 작업 증가 시 poll 조회 부하

## 외부 시스템과의 연동 방식 및 선택 이유
- Mock Worker가 webhook 대신 polling 모델이므로 서버 내부 스케줄러 기반 연동을 채택했습니다.
  - Pending 스케줄러: `POST /mock/process`
  - Processing 스케줄러: `GET /mock/process/{jobId}`
- 외부 호출 보호를 위해 수동 `CircuitBreaker.executeSupplier(...)`를 사용합니다.
- 선택 이유는 다음과 같습니다.
  - 생성 API 지연과 외부 장애를 분리하기 쉽습니다.
  - 재시도/복구/처리 보장(`at-least-once`) 설명이 단순해집니다.

## 참고
- 공식 제출용 설명은 `README.md`를 기준으로 합니다.

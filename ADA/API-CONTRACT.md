# ADA 사용자 API와 분석 계약

이번 버전은 사용자 업로드 취소에 더해 JOB/ATTEMPT 실제 서비스(요청/재시도/취소/claim/heartbeat/보고, CAS 트랜잭션)와
내부 Worker API(별도 토큰 인증)를 구현했다(2026-09-10, `AnalysisService`/`WorkerAnalysisController`, 로컬 테스트
`AnalysisServiceTest` 등으로 검증). **아직 없는 것**: 실제 Worker 프로세스(claim 루프)·MAT/스레드덤프 파서·Claude Code CLI
호출·결과 패키징, 그리고 목록 화면(JS/JSP)에서 이 API들을 실제로 호출하는 배선.

## 현재 제공하는 사용자 API

모든 API는 `/ADA` 컨텍스트, 기존 로그인 세션을 사용한다. 변경 요청은 POST와 세션별 `X-ADA-CSRF` 헤더가 필요하다. AJAX는 `X-Requested-With: XMLHttpRequest`를 보내며 세션 만료 시 401 JSON을 받는다. 상태 응답은 `Cache-Control: no-store`다. 타인/없는 ID는 404로 통일하고 관리자에게는 조회·취소만 추가 허용한다.

| 경로 | 메서드 | 입력/동작 |
|---|---|---|
| `/dump/uploadPrepare.do` | POST | URL-encoded `dumpType, deptNm, taskNm, hostNm, fileName, fileSize`. 검증 후 서버 UUID와 `UPLOAD_READY` 행 발급 |
| `/dump/upload.do` | POST | `X-ADA-Dump-ID` 헤더, multipart `dumpFile` 한 개. 본인만 전송. 준비 ID당 1회 claim |
| `/dump/uploadCancel.do` | POST | `dumpId`. 본인/관리자가 요청. 접수와 완료는 다름 |
| `/dump/uploadStatus.do` | GET | `dumpId`. 현재 업로드 상태·실패 원인·재업로드 가능 여부 |
| `/dump/analysisStatus.do` | GET | `dumpId`. 본인/관리자 JOB/ATTEMPT 현재 상태를 실제로 조회(`AnalysisResult`: statusCd/attemptNo/terminal/retryAllowed/cancelAllowed/resultAvailable 등). JOB이 없으면 `IDLE` |
| `/dump/analyzeRequest.do`, `/dump/analysisRetry.do` | POST | `dumpId`, 선택 `requestKey`(재전송 멱등키). 소유자만 가능(관리자도 대행 불가). STORED·미만료 덤프에서 IDLE/FAILED/CANCELLED일 때만 새 ATTEMPT를 원자적으로 등록, QUEUED/RUNNING/CANCEL_REQUESTED/RECOVERY_REQUIRED 중이면 새 ATTEMPT를 만들지 않고 현재 상태를 그대로 반환(중복 요청 거절) |
| `/dump/analysisCancel.do` | POST | `dumpId`. 본인/관리자. QUEUED(미claim)는 즉시 CANCELLED, RUNNING은 CANCEL_REQUESTED만 기록(실제 CANCELLED는 Worker의 report.do 확인 후). RECOVERY_REQUIRED는 사용자 취소로 풀리지 않음(관리자/Worker 재확인 필요) |
| `/dump/analyzeResultDownload.do` | GET | `dumpId`. 분석이 SUCCEEDED이고 결과 경로가 있어도 현재는 501(패키징 미구현)을 반환한다 — 실제 결과 다운로드는 아직 없음 |

## 내부 Worker API (`/worker/analysis/*`, 별도 토큰 인증)

`AdaWorkerAuthInterceptor`가 처리하며 사용자 세션/CSRF와 무관하다. `Authorization: Bearer <ada.worker.apiToken>` 헤더가
필요하고, `ada.worker.allowedIps`가 설정되면 발신 IP도 검사한다. 토큰이 설정되지 않으면 `/worker/**` 전체를 503으로
거절한다(fail closed). 아직 실제로 이 API를 호출하는 Worker 프로세스는 없다 — 계약과 서버 측 구현만 존재한다.

| 경로 | 입력/동작 |
|---|---|
| `/worker/analysis/claim.do` | `workerId`. 가장 오래된 QUEUED ATTEMPT 하나를 CAS로 claim하여 RUNNING으로 전환하고, 덤프 위치(dumpType/저장 파일명 등)를 함께 반환. 대기 건이 없으면 `available:false` |
| `/worker/analysis/heartbeat.do` | `attemptId`, `workerId`. 이 worker가 소유한 RUNNING/CANCEL_REQUESTED ATTEMPT만 갱신. 실패(409)면 이 worker는 더 이상 이 ATTEMPT의 소유자가 아니므로 즉시 중단하고 재확인해야 함 |
| `/worker/analysis/report.do` | `jobId,attemptId,workerId,expectedJobVersion,expectedAttemptVersion,expectedState,newState,failCode?,failReason?,resultPath?`. resultPath는 상대경로만 허용. 오래되었거나 예상과 다른 보고는 409 `STALE`로 무시(덮어쓰지 않음), 동일한 완료 보고 재전송은 그대로 성공 응답 |

업로드 응답의 `success`는 요청/상태 조회가 성공했다는 의미다. 업로드 완료는 `statusCd=STORED`, 취소 완료는 `statusCd=CANCELLED`일 때만 인정한다. `terminal`은 최종 상태, `retryAllowed`는 새 ID로 파일을 재선택하여 재업로드할 수 있음을 뜻한다. `UNKNOWN`과 통신 오류는 완료 증거가 아니다.

```text
UPLOAD_READY -> UPLOADING -> VALIDATING -> FINALIZING -> STORED
      |              |           |
      +--------------+-----------+--> CANCEL_REQUESTED
처리 종료 후 -> CLEANUP_PENDING -> CANCELLED 또는 FAILED
정리 실패 -> CLEANUP_FAILED (완료 아님, 관리자 확인)
STORED -> EXPIRED (기존 30일 보관 정책)
```

`VALIDATING -> FINALIZING` 조건부 UPDATE가 저장/취소 경쟁의 경계다. 취소가 먼저 반영되면 확정이 실패하고 처리자가 파일을 정리한다. 확정이 먼저면 늦은 취소로 파일을 삭제하지 않는다. 파일 이동 후 DB 오류는 상태를 다시 읽고 정리 권한을 확보한 뒤 처리한다. DB 확인이 실패하면 파일을 남기고 미확인 상태를 반환한다.

업로드/압축 검증은 협력적 취소 확인을 수행하고 전송 입력 스트림 닫기를 별도 제한된 실행기에서 시도한다. 실제 처리자가 읽기/쓰기/검증을 빠져나온 뒤에만 정리한다. 컨테이너 읽기 또는 NFS syscall이 멈추면 즉시 종료를 보장할 수 없다. 이때 `CANCEL_REQUESTED`를 유지한다. `xhr.abort()`는 네트워크 전송 중단일 뿐 완료 판정에 사용하지 않는다. 페이지를 닫거나 세션이 만료되어도 자동으로 취소 완료라고 표시하지 않는다.

## 향후 분석 데이터 모델

덤프당 JOB은 하나다(`DUMP_ID UNIQUE`). 작업 식별자는 재시도해도 유지하고, 실행마다 새 `ATTEMPT_ID`·증가하는 `ATTEMPT_NO`·새 멱등 요청 키를 만든다. 시도 이력을 덮어쓰지 않는다. JOB의 `CURRENT_ATTEMPT_ID`와 `STATUS_CD`는 현재 시도의 포인터/상태 요약이다. 실제 시작·종료·heartbeat·취소 요청자와 시간·실패 사유·결과 경로는 ATTEMPT에 저장한다.

복합 FK `(JOB_ID,CURRENT_ATTEMPT_ID)`는 다른 작업의 시도를 참조하지 못하게 한다. JOB의 `VERSION_NO`는 사용자 요청·claim·종료 전이에, ATTEMPT의 `VERSION_NO`는 개별 시도의 보고/heartbeat 동시 갱신에 사용한다. `REQUEST_KEY UNIQUE`는 같은 사용자 요청 재전송을 같은 시도로 돌려준다.

상태는 `IDLE`(시도 없음), `QUEUED`, `RUNNING`, `CANCEL_REQUESTED`, `CANCELLED`, `FAILED`, `SUCCEEDED`, `RECOVERY_REQUIRED`다. ATTEMPT에는 IDLE을 쓰지 않는다. `AnalysisState`는 허용 전이를 표현한 계약이며 실행 서비스가 아니다.

## 트랜잭션·보고 규칙 (`AnalysisService`에 구현·`AnalysisServiceTest`로 검증됨)

1. 최초 요청/재시도: 소유권·만료·원본 STORED를 확인한다. IDLE/FAILED/CANCELLED에서만 새 시도를 만든다. 실행 중, 취소 요청 중, 복구 확인 중에는 재시도하지 않는다. 종료 상태라도 실제 이전 프로세스 종료가 확인되어야 한다.
2. 같은 트랜잭션에서 JOB의 현재 ID/버전/상태를 확인해 잠그고 시도 INSERT, 현재 포인터/상태/버전 UPDATE, 덤프의 호환 상태 UPDATE를 수행한다. 어느 하나라도 조건 불일치/영향 행수 0이면 전부 롤백한다. 고정 잠금 순서는 JOB → ATTEMPT → DUMP다. DUMP 만료도 동일 규약에 참여하도록 Worker 연동 때 확장해야 한다.
3. claim: 현재 시도이며 QUEUED인 경우만 RUNNING으로 변경하고 worker ID·시작·heartbeat를 기록한다. 동일 시도를 중복 claim할 수 없다. 작업 중복 소비와 실제 프로세스 중복 실행 모두 방지해야 한다.
4. 취소: 사용자 요청은 먼저 CANCEL_REQUESTED와 요청자/시각을 기록한다. 아직 claim되지 않은 QUEUED 시도도 요청 상태를 거친 뒤 소비되지 않음을 트랜잭션으로 확인하고 종료할 수 있다. 실행 중은 실제 프로세스/하위 프로세스 종료와 작업 파일 정리를 확인한 보고만 CANCELLED로 인정한다.
5. 모든 상태 보고는 `jobId, attemptId, workerId, expectedJobVersion, expectedAttemptVersion, expectedState`를 포함한다. 현재 시도 ID·worker·버전·예상 상태가 모두 맞아야 반영한다. 늦은 보고는 409 stale 응답으로 무시하고 새 상태/결과 경로를 덮어쓰지 않는다. 동일한 완료 보고의 재전송은 이미 저장된 동일 결과를 읽어 반환한다.
6. heartbeat는 서버 수신 시각을 기록하며 현재 RUNNING/CANCEL_REQUESTED 시도만 갱신한다. 누락/만료 자체는 프로세스 종료 증거가 아니다. RECOVERY_REQUIRED로 두고 실행 종료를 확인하기 전에는 재시도/취소 완료를 만들지 않는다.
7. 완료 경쟁: CANCEL_REQUESTED가 먼저 확정되면 SUCCEEDED 보고를 거절한다. 성공이 먼저 확정되면 늦은 취소를 거절한다. 최종 상태와 END_DT 기록은 같은 트랜잭션이다. 취소 플래그·실패 코드/이유는 이력으로 보존한다.
8. 결과 경로는 시도별 허용 저장 루트 아래의 상대 경로만 허용한다. 실제 파일 존재/완성 여부와 다운로드 권한을 검증해야 한다. Worker 제공 임의 절대경로나 자격증명/명령 전문을 사용자 화면·로그에 기록하지 않는다.

현재 덤프 `ANALYSIS_STATUS_CD`의 `NOT_ANALYZED/ANALYZING/ANALYZED/ANALYSIS_FAILED`는 그대로 읽는다. 향후 JOB이 생긴 후에는 JOB/ATTEMPT가 원본이며 덤프 상태는 같은 트랜잭션에서 갱신하는 호환 표시 값이다. 매핑은 IDLE→NOT_ANALYZED, RUNNING→ANALYZING, SUCCEEDED→ANALYZED, FAILED→ANALYSIS_FAILED, 나머지 새 상태는 같은 이름이다. 기존 값만 보고 가짜 과거 시도를 만들지 않는다.

내부 Worker API는 사용자 세션 엔드포인트와 분리된 인증(토큰 + 선택적 IP 허용목록)으로 구현되어 있다(위 "내부 Worker API" 절). 다만 이 API를 실제로 호출하는 Worker 프로세스(claim 루프, MAT/스레드덤프 파서, Claude Code CLI 호출)는 아직 없다 — 서버 측 계약과 CAS 트랜잭션만 동작 중이다.

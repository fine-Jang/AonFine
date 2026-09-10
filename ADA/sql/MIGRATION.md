# 분석 작업 스키마 설치·이행·복구

이번 변경은 **SQL 준비만 수행**했다. 운영 CUBRID `mydb`에 접속하거나 DDL/DML을 적용하지 않았다. 서버 WAR 배포도 하지 않았다. 기존 JNDI `java:jboss/datasources/AonFineDS`를 유지한다. 비밀번호를 SQL/명령줄/소스/로그에 적지 않는다.

## 적용 대상과 순서

| 현재 설치 | 실행 순서 |
|---|---|
| ADA 테이블 없음 | `ada-schema-cubrid.sql` → `ada-analysis-tables-cubrid.sql` |
| `TB_ADA_DUMP`에 `DUMP_TYPE`, `HOST_NM` 있음 | `ada-migration-20260909-analysis-jobs.sql`의 사전 확인·백업 → 확인 후 `ada-analysis-tables-cubrid.sql` |
| 아직 `SERVICE_NM`을 사용하는 구형 | 이전 유형/호스트 이행 문서를 먼저 확인·완료한 후 위 기존 설치 순서 |
| 분석 테이블 일부/전체가 이미 있음 | 자동 재실행 금지. 실제 스키마를 읽어 누락된 단계만 적용 |

새 분석 테이블은 이번 준비 버전에서 조회/생성하지 않는다. 따라서 포털 업로드 취소 기능은 기존 `TB_ADA_DUMP` 스키마에서 작동하며, 분석 테이블 설치를 완료해도 Worker 기능은 활성화되지 않는다.

`TB_ADA_DUMP.STATUS_CD VARCHAR(20)`에 새 업로드 상태가 들어갈 수 있어 **덤프 컬럼 추가·이름 변경·기존 데이터 일괄 갱신은 없다**. 기존 `ANALYSIS_STATUS_CD`, `HEAP/THREAD`, `HOST_NM`, 실패 사유와 날짜도 그대로 보존한다. 신규 분석 테이블에 과거 실행을 추정하여 채워 넣지 않는다. 신규 설치 DDL의 기존 `UPLOADING` 기본값은 호환을 위해 유지하고 새 앱은 명시적으로 `UPLOAD_READY`를 삽입한다.

## DBA 적용 전 확인

1. 운영 CUBRID 버전, 테이블 소유자, JNDI 계정의 ADA 테이블 권한과 현재 스키마를 기록한다. 배포는 별도 승인 범위다.
2. ADA 쓰기를 멈추고 실행 중인 업로드를 정리한다. 구 WAR와 새 WAR를 동시에 같은 덤프 테이블에 쓰게 하지 않는다.
3. 외부 스키마+데이터 백업을 취한다. SQL의 `CREATE TABLE ... AS SELECT` 스냅샷은 데이터 비교용이며 인덱스·제약·권한까지 복원하는 백업이 아니다.
4. `TB_ADA_DUMP_BAK_20260909_JOBS` 및 새 테이블이 이미 있으면 중단하고 원인을 확인한다. 기존 백업을 덮어쓰거나 삭제하지 않는다.
5. 문장을 하나씩 실행하고 각 단계 결과를 기록한다. DDL 오류 시 전체가 자동 롤백된다고 가정하지 않는다. 이 문서는 CUBRID 버전/클라이언트의 DDL 커밋 동작을 실환경에서 검증하지 않았다.

실제 버전에서 PK/UNIQUE/FK 및 복합 FK를 확인해야 한다. CUBRID 문서는 일부 버전에서 CHECK가 파싱만 되고 무시됨을 명시한다. 기존 유형 CHECK나 신규 상태 CHECK만으로 값 검증이 된다고 가정하지 않는다. 앱의 유형·상태·CAS 검증을 유지한다. 참고: [CUBRID 테이블 정의](https://www.cubrid.org/manual/en/latest/sql/schema/table_stmt.html), [외래키와 PK 타입 관계](https://www.cubrid.org/manual/en/9.1.0/sql/schema/table.html).

## 적용 후 확인

```sql
SELECT COUNT(*) FROM TB_ADA_DUMP;
SELECT COUNT(*) FROM TB_ADA_DUMP_BAK_20260909_JOBS;
SELECT STATUS_CD, COUNT(*) FROM TB_ADA_DUMP GROUP BY STATUS_CD;
SELECT STATUS_CD, COUNT(*) FROM TB_ADA_DUMP_BAK_20260909_JOBS GROUP BY STATUS_CD;
SELECT COUNT(*) FROM TB_ADA_ANALYSIS_JOB;
SELECT COUNT(*) FROM TB_ADA_ANALYSIS_ATTEMPT;
```

덤프 건수·상태별 건수·실제 주요 컬럼을 사전 기록 및 백업과 비교한다. 새 분석 테이블은 둘 다 0건이어야 한다. 실제 스키마에 다음 항목이 있어야 한다.

- JOB: `JOB_ID` PK, `DUMP_ID` UNIQUE 및 DUMP FK, 현재 시도 복합 FK.
- ATTEMPT: `(JOB_ID, ATTEMPT_ID)` PK, 시도 ID UNIQUE, `(JOB_ID, ATTEMPT_NO)` UNIQUE, `REQUEST_KEY` UNIQUE, JOB FK.
- 상태/heartbeat 인덱스. 계정·맛집 등 기존 AonFine 테이블 변경 없음.

Worker 연동 전에 별도 검증 DB에서 중복 dump의 JOB 생성, 다른 JOB의 시도 참조, 중복 시도 번호/요청 키, 오래된 시도 ID와 버전의 UPDATE를 거절하는지 검증한다. 이 저장소의 `AnalysisSchemaTest`는 H2에서 타임스탬프 키워드만 바꿔 데이터 보존·제약·조건부 UPDATE를 검사한다. **CUBRID 문법/락/실제 JNDI 검증을 대신하지 않는다.**

## 이행 중 실패·복구

- 이번 이행은 기존 덤프를 변경하지 않는다. 실패하면 새 테이블의 생성 단계·제약 존재 여부를 확인하고 필요한 단계만 재개한다. 기본 복구는 **기존 덤프와 백업을 보존하고 새 테이블도 남겨두는 것**이다.
- 앱을 구 WAR로 되돌릴 때 분석 테이블은 남겨도 된다. 분석 테이블이 실제 사용되기 시작했다면 이력 보존을 위해 삭제하지 않는다.
- 구 UI는 새 업로드 상태를 이해하지 못한다. 구 WAR 복귀 전 새 업로드를 멈추고 모든 실행/정리를 확인한다. `CANCELLED` 등을 구 코드가 이해하는 상태로 바꿔야 한다면 대상 ID와 기존 행을 별도 백업하고 사유를 보존한 조건부 UPDATE를 DBA가 검토한다. 일괄 변환 SQL은 제공/자동 실행하지 않는다.
- 외부 백업 복원이 필요하면 이후 생성된 덤프·파일·이력을 먼저 별도 보존하고 DBA의 복구 절차를 따른다. 백업 테이블을 원본 이름으로 바꾸는 방식은 PK/FK/인덱스/권한과 신규 데이터를 잃을 수 있으므로 이 문서에서는 사용하지 않는다.

## 업로드 중단 후 남은 상태의 운영 처리

`UPLOAD_READY`는 아직 전송하지 않은 예약이다. 목록에서 선택 후 취소할 수 있다. 준비 응답 유실·탭 종료로 예약이 남아도 신규 ID를 중복 생성하지 않도록 사용자별 새 업로드를 차단한다.

`UPLOADING`, `VALIDATING`, `CANCEL_REQUESTED`, `FINALIZING`, `CLEANUP_PENDING`, `CLEANUP_FAILED`는 재시작/네트워크/DB/NFS 장애 후 남을 수 있다. 시간 경과만으로 실패·취소 완료로 바꾸거나 파일을 자동 삭제하지 않는다.

1. 해당 ADA 인스턴스의 업로드 요청/검증 처리자가 실제 종료했는지 확인한다. 다중 WAS 또는 구 WAR의 처리자가 남아 있으면 먼저 정지 여부를 확인한다.
2. NFS 마운트와 마커를 확인하고 정확한 덤프 ID의 `incoming/<UUID>.tar.gz.part`, `store/<UUID>.tar.gz`만 확인한다. `/DATA/Work`는 별도 Worker 영역으로 대상이 아니다.
3. DB 상태가 `STORED`라면 완료 응답 유실일 수 있으므로 원본을 지우지 않는다. `FINALIZING`은 이동/DB 커밋 결과가 불명확할 수 있어 DB와 파일을 대조한다.
4. 중단이 확인되고 정리가 필요한 경우 대상 파일을 보존/정리한 뒤 부재를 확인한다. 그런 다음에만 대상 ID와 예상 상태를 조건으로 `FAILED` 또는 `CANCELLED`, 원인, `MOD_DT`를 기록한다. 이미 완료된 상태는 덮어쓰지 않는다.
5. 정리나 DB 결과를 확인할 수 없으면 미확인 상태를 유지한다. 사용자에게 관리자 확인이 필요함을 알리고 재업로드를 보류한다.

WAS2 단일 업로드 인스턴스를 전제로 한다. 준비 단계의 사용자별 동시 제한은 JVM 내 직렬화+DB 활성 건수 확인이다. 다중 WAS 배포 전에는 DB 기반 사용자 admission lock/유일성 제약과 종료 소유권 규약이 추가로 필요하다. 동일 dump ID의 본문 claim/취소/저장 경쟁은 DB 조건부 UPDATE로 보호한다.

## 실제 적용 결과 (2026-09-10, Claude가 직접 실행)

운영 CUBRID `192.168.2.90:33000:mydb`에 `ada-migration-20260909-analysis-jobs.sql` → `ada-analysis-tables-cubrid.sql` 순서로 실제 적용했다. JNDI `java:jboss/datasources/AonFineDS`는 그대로 두고, 이 적용 자체는 별도로 전달받은 DB 접속정보로 직접 실행했다(비밀번호는 기록하지 않음). 서버 WAR는 재배포하지 않았다 - 현재 WAS2에 떠 있는 WAR는 이 분석 테이블을 전혀 참조하지 않는 이전 버전이라 이번 DDL과 충돌하지 않는다.

**사전 확인 결과**

- DB: `CUBRID 11.4.5.1866`, JDBC 드라이버 `11.1.0.0027`
- `TB_ADA_DUMP` 소유자: `DBA`, 컬럼에 `DUMP_TYPE`/`HOST_NM` 이미 존재 확인(기존 설치 경로로 진행)
- 적용 전 `TB_ADA_DUMP_BAK_20260909_JOBS`/`TB_ADA_ANALYSIS_JOB`/`TB_ADA_ANALYSIS_ATTEMPT` 전부 미존재 확인 → 진행
- `TB_ADA_DUMP` 1건(`STATUS_CD=STORED`, 실사용자 업로드), `UPLOADING`/`VALIDATING` 진행 중 건 없음 확인 → 쓰기 충돌 없이 진행

**적용 단계와 결과**

1. `CREATE TABLE TB_ADA_DUMP_BAK_20260909_JOBS AS SELECT * FROM TB_ADA_DUMP` — 성공, 1건 스냅샷(원본과 건수 일치 확인)
2. `CREATE TABLE TB_ADA_ANALYSIS_JOB (...)` — 성공
3. `CREATE TABLE TB_ADA_ANALYSIS_ATTEMPT (...)` — 성공
4. `ALTER TABLE TB_ADA_ANALYSIS_JOB ADD CONSTRAINT FK_ADA_JOB_CURRENT FOREIGN KEY (JOB_ID, CURRENT_ATTEMPT_ID) REFERENCES TB_ADA_ANALYSIS_ATTEMPT (JOB_ID, ATTEMPT_ID)` — 성공
5. `CREATE INDEX IX_ADA_JOB_STATUS ...`, `CREATE INDEX IX_ADA_ATTEMPT_HEARTBEAT ...` — 둘 다 성공

**적용 후 검증(실제 스키마 메타데이터로 확인, 존재 여부를 문서상 가정하지 않음)**

- `TB_ADA_ANALYSIS_JOB`: PK(`JOB_ID`), UNIQUE(`DUMP_ID`), FK(`DUMP_ID`→`TB_ADA_DUMP.DUMP_ID`), 복합 FK(`JOB_ID,CURRENT_ATTEMPT_ID`→`TB_ADA_ANALYSIS_ATTEMPT`) 전부 실제 카탈로그에 존재
- `TB_ADA_ANALYSIS_ATTEMPT`: 복합 PK(`JOB_ID,ATTEMPT_ID`), UNIQUE(`ATTEMPT_ID`), UNIQUE(`JOB_ID,ATTEMPT_NO`), UNIQUE(`REQUEST_KEY`), FK(`JOB_ID`→`TB_ADA_ANALYSIS_JOB.JOB_ID`) 전부 실제 카탈로그에 존재
- 건수: `TB_ADA_DUMP`=1(변경 없음), `TB_ADA_DUMP_BAK_20260909_JOBS`=1(일치), `TB_ADA_ANALYSIS_JOB`=0, `TB_ADA_ANALYSIS_ATTEMPT`=0(둘 다 가짜 이력 없이 빈 상태로 확인)
- 기존 `TB_USER`/`TB_RESTAURANT`/`TB_BOARD`/`TB_LUNCH_VOTE` 등은 이번 세션에서 조회조차 하지 않았다

**이번 적용에 대한 복구 절차**

문제가 발견되면(이번 적용 자체는 전부 성공이라 즉시 필요하지 않음) 아래 역순으로 되돌린다. `TB_ADA_DUMP`는 이 마이그레이션에서 전혀 변경하지 않았으므로 되돌릴 대상이 아니다.

```sql
ALTER TABLE TB_ADA_ANALYSIS_JOB DROP CONSTRAINT FK_ADA_JOB_CURRENT;
DROP TABLE TB_ADA_ANALYSIS_ATTEMPT;
DROP TABLE TB_ADA_ANALYSIS_JOB;
-- TB_ADA_DUMP_BAK_20260909_JOBS는 데이터 스냅샷이므로 확인 목적이 끝나기 전까지 삭제하지 않는다.
```

**남은 일**: 실제 Worker 연동(JOB/ATTEMPT INSERT, claim, heartbeat, 상태 전이 트랜잭션)은 이번 범위가 아니며 API-CONTRACT.md의 계약대로 별도 구현이 필요하다. 서버 WAR 재배포도 이번에 하지 않았다 - 필요 시 별도 확인 후 진행한다.

-- ADA 마이그레이션: DUMP_TYPE 추가, SERVICE_NM -> HOST_NM 컬럼명 변경
-- 적용 대상: TB_ADA_DUMP가 이미 구 스키마(SERVICE_NM, DUMP_TYPE 없음)로 설치되어 있고
--           데이터가 존재할 수 있는 환경. 신규 설치는 ada-schema-cubrid.sql을 대신 사용한다.
--
-- 2026-09-09에 실제 운영 CUBRID(192.168.2.90:33000:mydb)에 이 순서 그대로 적용하고 확인했다.
-- 그 시점 TB_ADA_DUMP는 0건이었지만, 데이터가 있는 환경에서도 안전하도록 백업 단계를 포함한다.
--
-- CUBRID DDL 롤백 한계에 대한 메모:
--   CUBRID는 각 DDL 문이 사실상 자동커밋에 가깝게 동작해 여러 ALTER TABLE을 하나의 트랜잭션으로
--   묶어 통째로 롤백하는 것을 신뢰할 수 없다. 그래서 "실패하면 트랜잭션을 롤백" 하는 방식 대신,
--   1) 먼저 원본을 그대로 복제한 백업 테이블을 만들고,
--   2) 각 단계를 하나씩 적용하며 매 단계 뒤에 결과를 확인하고,
--   3) 문제가 생기면 아래 "복구 절차"로 백업 테이블에서 원래 상태를 재구성한다.

-- 1) 백업 (반드시 먼저 실행)
CREATE TABLE TB_ADA_DUMP_BACKUP_20260909 AS SELECT * FROM TB_ADA_DUMP;

-- 2) DUMP_TYPE 컬럼을 nullable로 추가
ALTER TABLE TB_ADA_DUMP ADD COLUMN DUMP_TYPE VARCHAR(10);

-- 3) 기존 데이터는 전부 HEAP으로 채운다 (이번 마이그레이션 이전에는 heap 덤프만 다뤘으므로)
UPDATE TB_ADA_DUMP SET DUMP_TYPE = 'HEAP' WHERE DUMP_TYPE IS NULL;

-- 4) 백필 후 NOT NULL + 기본값 확정
ALTER TABLE TB_ADA_DUMP CHANGE DUMP_TYPE DUMP_TYPE VARCHAR(10) DEFAULT 'HEAP' NOT NULL;

-- 5) 허용값 검증(HEAP/THREAD)을 DB 레벨에서도 강제 (애플리케이션 검증과 별개의 방어선)
ALTER TABLE TB_ADA_DUMP ADD CONSTRAINT CK_TB_ADA_DUMP_TYPE CHECK (DUMP_TYPE IN ('HEAP', 'THREAD'));

-- 6) SERVICE_NM -> HOST_NM 컬럼명 변경 (값은 그대로 보존됨)
ALTER TABLE TB_ADA_DUMP RENAME COLUMN SERVICE_NM AS HOST_NM;

-- ===== 적용 후 확인 =====
-- SELECT DUMP_ID, DUMP_TYPE, HOST_NM FROM TB_ADA_DUMP;
-- 컬럼 목록에 SERVICE_NM이 더 이상 없고 HOST_NM과 DUMP_TYPE이 보여야 한다.
-- SELECT COUNT(*) FROM TB_ADA_DUMP;  -- 이 값이 TB_ADA_DUMP_BACKUP_20260909의 건수와 같아야 한다.

-- ===== 복구 절차 (문제 발생 시) =====
-- CUBRID는 컬럼명을 되돌리는 것도 별도 DDL이 필요하고, 중간에 실패한 상태에서는 정확히
-- 어느 단계까지 적용됐는지 위 "적용 후 확인" 조회로 먼저 파악한 뒤 아래를 상황에 맞게 적용한다.
--
-- HOST_NM을 SERVICE_NM으로 되돌리는 경우:
--   ALTER TABLE TB_ADA_DUMP RENAME COLUMN HOST_NM AS SERVICE_NM;
--
-- DUMP_TYPE 관련 변경을 전부 되돌리는 경우:
--   ALTER TABLE TB_ADA_DUMP DROP CONSTRAINT CK_TB_ADA_DUMP_TYPE;
--   ALTER TABLE TB_ADA_DUMP DROP COLUMN DUMP_TYPE;
--
-- 완전히 원래 상태로 되돌려야 하는 경우(최후의 수단, 그 사이 신규로 들어온 데이터는 유실됨):
--   DROP TABLE TB_ADA_DUMP;
--   RENAME TABLE TB_ADA_DUMP_BACKUP_20260909 AS TB_ADA_DUMP;
--   (그 후 인덱스/제약조건을 ada-schema-cubrid.sql 이전 버전 기준으로 다시 생성해야 한다)
--
-- 확인이 끝나면 TB_ADA_DUMP_BACKUP_20260909는 더 이상 필요하지 않을 때 DBA 판단으로 정리한다.
-- 이 스크립트는 백업 테이블을 자동으로 지우지 않는다.

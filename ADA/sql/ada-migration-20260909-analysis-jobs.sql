-- Existing installation, with DUMP_TYPE and HOST_NM already present.
-- Local preparation only: this script has NOT been applied to the real DB in this change.
-- Read MIGRATION.md first. Stop ADA writes and take an external schema+data backup.
-- Execute one statement at a time, stop immediately on error. Do not assume DDL rollback.

-- 1. Preflight: record the schema, row count and status distribution before any DDL.
SELECT COUNT(*) AS DUMP_COUNT FROM TB_ADA_DUMP;
SELECT STATUS_CD, COUNT(*) AS STATUS_COUNT FROM TB_ADA_DUMP GROUP BY STATUS_CD;
SELECT DUMP_ID, DUMP_TYPE, HOST_NM FROM TB_ADA_DUMP WHERE 1 = 0;

-- 2. Data snapshot only (does NOT preserve indexes/constraints/grants).
-- If this name already exists, STOP. Never overwrite/delete an earlier backup.
CREATE TABLE TB_ADA_DUMP_BAK_20260909_JOBS AS SELECT * FROM TB_ADA_DUMP;
SELECT COUNT(*) AS BACKUP_COUNT FROM TB_ADA_DUMP_BAK_20260909_JOBS;

-- 3. STOP HERE and verify counts/schema. Then separately execute:
--    ada-analysis-tables-cubrid.sql
-- No TB_ADA_DUMP column addition, rename, status rewrite or destructive DDL is required.
-- TB_ADA_DUMP.STATUS_CD already fits the new upload states (VARCHAR(20)).
-- Existing ANALYSIS_STATUS_CD values remain unchanged; no fake attempts are backfilled.
-- Postflight/recovery instructions and SQL are in MIGRATION.md.

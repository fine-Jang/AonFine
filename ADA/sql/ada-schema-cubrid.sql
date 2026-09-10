-- ADA (덤프 분석 포털) 전용 신규 테이블 - 신규 설치 기준 (DUMP_TYPE/HOST_NM 포함 최신 스키마)
-- 이 SQL은 자동 적용되지 않는다. DBA/운영 담당자가 기존 AonFine CUBRID 인스턴스에 수동으로 적용해야 한다.
-- 기존 TB_USER, TB_RESTAURANT, TB_BOARD, TB_LUNCH_VOTE 등 기존 테이블은 절대 변경하지 않는다.
-- TB_USER는 ADA에서 조회(SELECT)만 하며 계정 생성/수정 기능은 ADA에 포함하지 않는다.
--
-- 이미 구 버전(HOST_NM 이전, SERVICE_NM 컬럼)으로 설치되어 데이터가 있는 환경은 이 파일 대신
-- ada-migration-20260909-dump-type-host-nm.sql을 사용한다.
-- 신규 설치는 이 파일 실행 후 ada-analysis-tables-cubrid.sql을 실행한다.
-- DUMP_TYPE/HOST_NM이 이미 있는 기존 설치는 ada-migration-20260909-analysis-jobs.sql과 MIGRATION.md를 따른다.

CREATE TABLE TB_ADA_DUMP (
    DUMP_ID       VARCHAR(36) NOT NULL,               -- UUID 문자열 (내부 저장 파일명과 동일 값 사용)
    USER_ID       VARCHAR(50) NOT NULL,                -- TB_USER.USER_ID 참조 (물리적 FK 없음, 기존 프로젝트 관례 준수)
    DUMP_TYPE     VARCHAR(10) DEFAULT 'HEAP' NOT NULL, -- HEAP 또는 THREAD
    DEPT_NM       VARCHAR(200) NOT NULL,               -- 부처명
    TASK_NM       VARCHAR(200) NOT NULL,               -- 업무명
    HOST_NM       VARCHAR(200) NOT NULL,               -- 호스트명 (덤프를 채취한 서버)
    ORIGINAL_FILE_NM VARCHAR(500) NOT NULL,             -- 사용자가 올린 원본 파일명 (표시/다운로드 전용)
    STORED_FILE_NM   VARCHAR(80) NOT NULL,              -- 실제 저장 파일명 (UUID.tar.gz, 경로 안전)
    FILE_SIZE     NUMERIC(20,0) NOT NULL,               -- 바이트 단위, 5,000,000,000 미만
    STATUS_CD     VARCHAR(20) DEFAULT 'UPLOADING' NOT NULL,
                    -- UPLOAD_READY / UPLOADING / VALIDATING / FINALIZING / STORED / FAILED / EXPIRED
                    -- CANCEL_REQUESTED / CLEANUP_PENDING / CLEANUP_FAILED / CANCELLED
                    -- 분석 상태(향후 연동): NOT_ANALYZED(=STORED와 동일 취급) / ANALYZING / ANALYZED / ANALYSIS_FAILED
    ANALYSIS_STATUS_CD VARCHAR(20) DEFAULT 'NOT_ANALYZED' NOT NULL,
    FAIL_REASON   VARCHAR(1000),                        -- 실패 시 원인 메시지
    EXPIRED_YN    CHAR(1) DEFAULT 'N' NOT NULL,          -- 보관 만료(30일 경과) 여부. 실제 만료 판정은 REG_DT 기준으로 조회 시점에 재계산하며
                                                          -- 이 컬럼은 배치가 정리한 뒤의 표시/이력용 플래그다.
    REG_DT        DATETIME DEFAULT CURRENT_DATETIME NOT NULL,   -- 업로드 완료(STORED로 전환된) 시각. 보관기간 30일의 기준.
    MOD_DT        DATETIME,
    CLEANED_DT    DATETIME,                              -- 정리(파일 삭제) 배치가 실제로 처리한 시각
    CONSTRAINT PK_TB_ADA_DUMP PRIMARY KEY (DUMP_ID),
    CONSTRAINT CK_TB_ADA_DUMP_TYPE CHECK (DUMP_TYPE IN ('HEAP', 'THREAD'))
);

-- 목록 조회(본인 내역/전체, 상태별, 최신순) 성능을 위한 인덱스
CREATE INDEX IX_TB_ADA_DUMP_USER_REG_DT ON TB_ADA_DUMP (USER_ID, REG_DT);
CREATE INDEX IX_TB_ADA_DUMP_STATUS_REG_DT ON TB_ADA_DUMP (STATUS_CD, REG_DT);
CREATE UNIQUE INDEX UX_TB_ADA_DUMP_STORED_FILE_NM ON TB_ADA_DUMP (STORED_FILE_NM);

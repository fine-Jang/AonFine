package com.aonfine.ada.analysis.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;

import javax.annotation.Resource;
import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import com.aonfine.ada.analysis.AnalysisAttemptVO;
import com.aonfine.ada.analysis.AnalysisJobVO;
import com.aonfine.ada.analysis.AnalysisMapper;

@Repository("analysisMapper")
public class AnalysisJdbcMapper implements AnalysisMapper {

    private static final String JOB_COLS = "JOB_ID, DUMP_ID, CURRENT_ATTEMPT_ID, STATUS_CD, VERSION_NO, REQUESTED_BY, "
            + "TO_CHAR(REG_DT, 'YYYY-MM-DD HH24:MI:SS') AS REG_DT, TO_CHAR(MOD_DT, 'YYYY-MM-DD HH24:MI:SS') AS MOD_DT";
    private static final String ATTEMPT_COLS = "JOB_ID, ATTEMPT_ID, ATTEMPT_NO, REQUEST_KEY, STATUS_CD, VERSION_NO, "
            + "CANCEL_REQUESTED_YN, CANCEL_REQUESTED_BY, TO_CHAR(CANCEL_REQUESTED_DT, 'YYYY-MM-DD HH24:MI:SS') AS CANCEL_REQUESTED_DT, "
            + "REQUESTED_BY, WORKER_ID, TO_CHAR(START_DT, 'YYYY-MM-DD HH24:MI:SS') AS START_DT, "
            + "TO_CHAR(END_DT, 'YYYY-MM-DD HH24:MI:SS') AS END_DT, TO_CHAR(LAST_HEARTBEAT_DT, 'YYYY-MM-DD HH24:MI:SS') AS LAST_HEARTBEAT_DT, "
            + "FAIL_CODE, FAIL_REASON, RESULT_PATH, "
            + "TO_CHAR(REG_DT, 'YYYY-MM-DD HH24:MI:SS') AS REG_DT, TO_CHAR(MOD_DT, 'YYYY-MM-DD HH24:MI:SS') AS MOD_DT";

    private JdbcTemplate jdbcTemplate;

    @Resource(name = "dataSource")
    public void setDataSource(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Override
    public void insertJob(AnalysisJobVO vo) {
        jdbcTemplate.update("INSERT INTO TB_ADA_ANALYSIS_JOB (JOB_ID, DUMP_ID, STATUS_CD, VERSION_NO, REQUESTED_BY) "
                + "VALUES (?, ?, 'IDLE', 0, ?)", vo.getJobId(), vo.getDumpId(), vo.getRequestedBy());
    }

    @Override
    public AnalysisJobVO selectJobByDumpId(String dumpId) {
        List<AnalysisJobVO> list = jdbcTemplate.query("SELECT " + JOB_COLS + " FROM TB_ADA_ANALYSIS_JOB WHERE DUMP_ID = ?",
                new Object[] { dumpId }, new JobRowMapper());
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public AnalysisJobVO selectJobById(String jobId) {
        List<AnalysisJobVO> list = jdbcTemplate.query("SELECT " + JOB_COLS + " FROM TB_ADA_ANALYSIS_JOB WHERE JOB_ID = ?",
                new Object[] { jobId }, new JobRowMapper());
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public void insertAttempt(AnalysisAttemptVO vo) {
        jdbcTemplate.update("INSERT INTO TB_ADA_ANALYSIS_ATTEMPT (JOB_ID, ATTEMPT_ID, ATTEMPT_NO, REQUEST_KEY, STATUS_CD, "
                + "VERSION_NO, REQUESTED_BY) VALUES (?, ?, ?, ?, 'QUEUED', 0, ?)",
                vo.getJobId(), vo.getAttemptId(), vo.getAttemptNo(), vo.getRequestKey(), vo.getRequestedBy());
    }

    @Override
    public int selectMaxAttemptNo(String jobId) {
        Integer max = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(ATTEMPT_NO), 0) FROM TB_ADA_ANALYSIS_ATTEMPT WHERE JOB_ID = ?",
                new Object[] { jobId }, Integer.class);
        return max == null ? 0 : max;
    }

    @Override
    public AnalysisAttemptVO selectAttempt(String jobId, String attemptId) {
        List<AnalysisAttemptVO> list = jdbcTemplate.query(
                "SELECT " + ATTEMPT_COLS + " FROM TB_ADA_ANALYSIS_ATTEMPT WHERE JOB_ID = ? AND ATTEMPT_ID = ?",
                new Object[] { jobId, attemptId }, new AttemptRowMapper());
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public AnalysisAttemptVO selectAttemptById(String attemptId) {
        List<AnalysisAttemptVO> list = jdbcTemplate.query(
                "SELECT " + ATTEMPT_COLS + " FROM TB_ADA_ANALYSIS_ATTEMPT WHERE ATTEMPT_ID = ?",
                new Object[] { attemptId }, new AttemptRowMapper());
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public AnalysisAttemptVO selectAttemptByRequestKey(String requestKey) {
        List<AnalysisAttemptVO> list = jdbcTemplate.query(
                "SELECT " + ATTEMPT_COLS + " FROM TB_ADA_ANALYSIS_ATTEMPT WHERE REQUEST_KEY = ?",
                new Object[] { requestKey }, new AttemptRowMapper());
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public int registerNewAttempt(String jobId, String expectedStatus, long expectedVersion, String newAttemptId, int newAttemptNo) {
        return jdbcTemplate.update("UPDATE TB_ADA_ANALYSIS_JOB SET CURRENT_ATTEMPT_ID = ?, STATUS_CD = 'QUEUED', "
                + "VERSION_NO = VERSION_NO + 1, MOD_DT = CURRENT_DATETIME "
                + "WHERE JOB_ID = ? AND STATUS_CD = ? AND VERSION_NO = ?",
                newAttemptId, jobId, expectedStatus, expectedVersion);
    }

    @Override
    public int updateJobStatus(String jobId, String newStatus, String expectedCurrentAttemptId, String expectedStatus, long expectedVersion) {
        return jdbcTemplate.update("UPDATE TB_ADA_ANALYSIS_JOB SET STATUS_CD = ?, VERSION_NO = VERSION_NO + 1, MOD_DT = CURRENT_DATETIME "
                + "WHERE JOB_ID = ? AND CURRENT_ATTEMPT_ID = ? AND STATUS_CD = ? AND VERSION_NO = ?",
                newStatus, jobId, expectedCurrentAttemptId, expectedStatus, expectedVersion);
    }

    @Override
    public int claimAttempt(String attemptId, String workerId, long expectedVersion) {
        return jdbcTemplate.update("UPDATE TB_ADA_ANALYSIS_ATTEMPT SET STATUS_CD = 'RUNNING', WORKER_ID = ?, "
                + "START_DT = CURRENT_DATETIME, LAST_HEARTBEAT_DT = CURRENT_DATETIME, VERSION_NO = VERSION_NO + 1, MOD_DT = CURRENT_DATETIME "
                + "WHERE ATTEMPT_ID = ? AND STATUS_CD = 'QUEUED' AND VERSION_NO = ?",
                workerId, attemptId, expectedVersion);
    }

    @Override
    public int updateHeartbeat(String attemptId, String workerId) {
        return jdbcTemplate.update("UPDATE TB_ADA_ANALYSIS_ATTEMPT SET LAST_HEARTBEAT_DT = CURRENT_DATETIME, MOD_DT = CURRENT_DATETIME "
                + "WHERE ATTEMPT_ID = ? AND WORKER_ID = ? AND STATUS_CD IN ('RUNNING', 'CANCEL_REQUESTED')",
                attemptId, workerId);
    }

    @Override
    public int cancelQueuedAttempt(String attemptId, long expectedVersion, String cancelledBy) {
        return jdbcTemplate.update("UPDATE TB_ADA_ANALYSIS_ATTEMPT SET STATUS_CD = 'CANCELLED', END_DT = CURRENT_DATETIME, "
                + "CANCEL_REQUESTED_YN = 'Y', CANCEL_REQUESTED_BY = ?, CANCEL_REQUESTED_DT = CURRENT_DATETIME, "
                + "VERSION_NO = VERSION_NO + 1, MOD_DT = CURRENT_DATETIME "
                + "WHERE ATTEMPT_ID = ? AND STATUS_CD = 'QUEUED' AND VERSION_NO = ?",
                cancelledBy, attemptId, expectedVersion);
    }

    @Override
    public int markCancelRequested(String attemptId, String expectedStatus, long expectedVersion, String requestedBy) {
        return jdbcTemplate.update("UPDATE TB_ADA_ANALYSIS_ATTEMPT SET STATUS_CD = 'CANCEL_REQUESTED', CANCEL_REQUESTED_YN = 'Y', "
                + "CANCEL_REQUESTED_BY = ?, CANCEL_REQUESTED_DT = CURRENT_DATETIME, VERSION_NO = VERSION_NO + 1, MOD_DT = CURRENT_DATETIME "
                + "WHERE ATTEMPT_ID = ? AND STATUS_CD = ? AND VERSION_NO = ?",
                requestedBy, attemptId, expectedStatus, expectedVersion);
    }

    @Override
    public int finalizeAttempt(String attemptId, String workerId, long expectedVersion, String expectedStatus, String newStatus,
            String failCode, String failReason, String resultPath) {
        return jdbcTemplate.update(con -> {
            java.sql.PreparedStatement ps = con.prepareStatement(
                    "UPDATE TB_ADA_ANALYSIS_ATTEMPT SET STATUS_CD = ?, END_DT = CURRENT_DATETIME, FAIL_CODE = ?, "
                    + "FAIL_REASON = ?, RESULT_PATH = ?, VERSION_NO = VERSION_NO + 1, MOD_DT = CURRENT_DATETIME "
                    + "WHERE ATTEMPT_ID = ? AND WORKER_ID = ? AND STATUS_CD = ? AND VERSION_NO = ?");
            ps.setString(1, newStatus);
            setNullableString(ps, 2, failCode);
            setNullableString(ps, 3, truncate(failReason, 1000));
            setNullableString(ps, 4, resultPath);
            ps.setString(5, attemptId);
            ps.setString(6, workerId);
            ps.setString(7, expectedStatus);
            ps.setLong(8, expectedVersion);
            return ps;
        });
    }

    @Override
    public int markRecoveryRequired(String attemptId, String expectedStatus, long expectedVersion) {
        return jdbcTemplate.update("UPDATE TB_ADA_ANALYSIS_ATTEMPT SET STATUS_CD = 'RECOVERY_REQUIRED', "
                + "VERSION_NO = VERSION_NO + 1, MOD_DT = CURRENT_DATETIME "
                + "WHERE ATTEMPT_ID = ? AND STATUS_CD = ? AND VERSION_NO = ?",
                attemptId, expectedStatus, expectedVersion);
    }

    @Override
    public List<AnalysisAttemptVO> selectOldestQueued(int limit) {
        return jdbcTemplate.query("SELECT " + ATTEMPT_COLS + " FROM TB_ADA_ANALYSIS_ATTEMPT WHERE STATUS_CD = 'QUEUED' "
                + "ORDER BY REG_DT ASC LIMIT ?", new Object[] { limit }, new AttemptRowMapper());
    }

    @Override
    public List<AnalysisAttemptVO> selectStaleActive(Timestamp heartbeatCutoff, int limit) {
        return jdbcTemplate.query("SELECT " + ATTEMPT_COLS + " FROM TB_ADA_ANALYSIS_ATTEMPT "
                + "WHERE STATUS_CD IN ('RUNNING', 'CANCEL_REQUESTED') AND (LAST_HEARTBEAT_DT IS NULL OR LAST_HEARTBEAT_DT < ?) "
                + "ORDER BY LAST_HEARTBEAT_DT ASC LIMIT ?", new Object[] { heartbeatCutoff, limit }, new AttemptRowMapper());
    }

    private static void setNullableString(java.sql.PreparedStatement ps, int index, String value) throws SQLException {
        if (StringUtils.hasText(value)) {
            ps.setString(index, value);
        } else {
            ps.setNull(index, Types.VARCHAR);
        }
    }

    private static String truncate(String value, int max) {
        return value != null && value.length() > max ? value.substring(0, max) : value;
    }

    private static class JobRowMapper implements RowMapper<AnalysisJobVO> {
        @Override
        public AnalysisJobVO mapRow(ResultSet rs, int rowNum) throws SQLException {
            AnalysisJobVO vo = new AnalysisJobVO();
            vo.setJobId(rs.getString("JOB_ID"));
            vo.setDumpId(rs.getString("DUMP_ID"));
            vo.setCurrentAttemptId(rs.getString("CURRENT_ATTEMPT_ID"));
            vo.setStatusCd(rs.getString("STATUS_CD"));
            vo.setVersionNo(rs.getLong("VERSION_NO"));
            vo.setRequestedBy(rs.getString("REQUESTED_BY"));
            vo.setRegDt(rs.getString("REG_DT"));
            vo.setModDt(rs.getString("MOD_DT"));
            return vo;
        }
    }

    private static class AttemptRowMapper implements RowMapper<AnalysisAttemptVO> {
        @Override
        public AnalysisAttemptVO mapRow(ResultSet rs, int rowNum) throws SQLException {
            AnalysisAttemptVO vo = new AnalysisAttemptVO();
            vo.setJobId(rs.getString("JOB_ID"));
            vo.setAttemptId(rs.getString("ATTEMPT_ID"));
            vo.setAttemptNo(rs.getInt("ATTEMPT_NO"));
            vo.setRequestKey(rs.getString("REQUEST_KEY"));
            vo.setStatusCd(rs.getString("STATUS_CD"));
            vo.setVersionNo(rs.getLong("VERSION_NO"));
            vo.setCancelRequestedYn(rs.getString("CANCEL_REQUESTED_YN"));
            vo.setCancelRequestedBy(rs.getString("CANCEL_REQUESTED_BY"));
            vo.setCancelRequestedDt(rs.getString("CANCEL_REQUESTED_DT"));
            vo.setRequestedBy(rs.getString("REQUESTED_BY"));
            vo.setWorkerId(rs.getString("WORKER_ID"));
            vo.setStartDt(rs.getString("START_DT"));
            vo.setEndDt(rs.getString("END_DT"));
            vo.setLastHeartbeatDt(rs.getString("LAST_HEARTBEAT_DT"));
            vo.setFailCode(rs.getString("FAIL_CODE"));
            vo.setFailReason(rs.getString("FAIL_REASON"));
            vo.setResultPath(rs.getString("RESULT_PATH"));
            vo.setRegDt(rs.getString("REG_DT"));
            vo.setModDt(rs.getString("MOD_DT"));
            return vo;
        }
    }
}

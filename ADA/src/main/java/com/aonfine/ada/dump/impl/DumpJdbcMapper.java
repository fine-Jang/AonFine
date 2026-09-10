package com.aonfine.ada.dump.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Resource;
import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import com.aonfine.ada.dump.DumpMapper;
import com.aonfine.ada.dump.DumpSearchVO;
import com.aonfine.ada.dump.DumpVO;

@Repository("dumpMapper")
public class DumpJdbcMapper implements DumpMapper {

    private JdbcTemplate jdbcTemplate;

    @Resource(name = "dataSource")
    public void setDataSource(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Override
    public void insertUploading(DumpVO vo) {
        String sql = "INSERT INTO TB_ADA_DUMP "
                + "(DUMP_ID, USER_ID, DUMP_TYPE, DEPT_NM, TASK_NM, HOST_NM, ORIGINAL_FILE_NM, STORED_FILE_NM, FILE_SIZE, STATUS_CD) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'UPLOAD_READY')";
        jdbcTemplate.update(sql, vo.getDumpId(), vo.getUserId(), vo.getDumpType(), vo.getDeptNm(), vo.getTaskNm(),
                vo.getHostNm(), vo.getOriginalFileNm(), vo.getStoredFileNm(), vo.getFileSize());
    }

    @Override
    public int transition(String dumpId, String expectedStatus, String statusCd, String failReason) {
        String sql = "UPDATE TB_ADA_DUMP SET STATUS_CD = ?, FAIL_REASON = ?, MOD_DT = CURRENT_DATETIME WHERE DUMP_ID = ? AND STATUS_CD = ?";
        return jdbcTemplate.update(sql, ps -> {
            ps.setString(1, statusCd);
            if (StringUtils.hasText(failReason)) {
                ps.setString(2, failReason.length() > 1000 ? failReason.substring(0, 1000) : failReason);
            } else {
                ps.setNull(2, Types.VARCHAR);
            }
            ps.setString(3, dumpId);
            ps.setString(4, expectedStatus);
        });
    }

    @Override
    public int markStored(String dumpId, String originalFileNm, String storedFileNm, long fileSize) {
        String sql = "UPDATE TB_ADA_DUMP SET STATUS_CD = 'STORED', ORIGINAL_FILE_NM = ?, STORED_FILE_NM = ?, "
                + "FILE_SIZE = ?, REG_DT = CURRENT_DATETIME, MOD_DT = CURRENT_DATETIME, FAIL_REASON = NULL "
                + "WHERE DUMP_ID = ? AND STATUS_CD = 'FINALIZING'";
        return jdbcTemplate.update(sql, originalFileNm, storedFileNm, fileSize, dumpId);
    }

    @Override
    public DumpVO selectOne(String dumpId, Timestamp cutoff) {
        String sql = "SELECT DUMP_ID, USER_ID, DUMP_TYPE, DEPT_NM, TASK_NM, HOST_NM, ORIGINAL_FILE_NM, STORED_FILE_NM, "
                + "FILE_SIZE, STATUS_CD, ANALYSIS_STATUS_CD, FAIL_REASON, EXPIRED_YN, "
                + "TO_CHAR(REG_DT, 'YYYY-MM-DD HH24:MI:SS') AS REG_DT, "
                + "CASE WHEN REG_DT < ? THEN 1 ELSE 0 END AS EXPIRED_NOW "
                + "FROM TB_ADA_DUMP WHERE DUMP_ID = ?";
        List<DumpVO> list = jdbcTemplate.query(sql, new Object[] { cutoff, dumpId }, new DumpRowMapper());
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public List<DumpVO> selectList(DumpSearchVO searchVO, Timestamp cutoff) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT DUMP_ID, USER_ID, DUMP_TYPE, DEPT_NM, TASK_NM, HOST_NM, ORIGINAL_FILE_NM, STORED_FILE_NM, ")
                .append("FILE_SIZE, STATUS_CD, ANALYSIS_STATUS_CD, FAIL_REASON, EXPIRED_YN, ")
                .append("TO_CHAR(REG_DT, 'YYYY-MM-DD HH24:MI:SS') AS REG_DT, ")
                .append("CASE WHEN REG_DT < ? THEN 1 ELSE 0 END AS EXPIRED_NOW ")
                .append("FROM TB_ADA_DUMP WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        params.add(cutoff);
        appendSearchConditions(sql, params, searchVO);
        sql.append(" ORDER BY REG_DT DESC LIMIT ?, ?");
        params.add(searchVO.getFirstIndex());
        params.add(searchVO.getPageSize());
        return jdbcTemplate.query(sql.toString(), params.toArray(), new DumpRowMapper());
    }

    @Override
    public int countList(DumpSearchVO searchVO) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM TB_ADA_DUMP WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        appendSearchConditions(sql, params, searchVO);
        Integer count = jdbcTemplate.queryForObject(sql.toString(), params.toArray(), Integer.class);
        return count == null ? 0 : count;
    }

    private void appendSearchConditions(StringBuilder sql, List<Object> params, DumpSearchVO searchVO) {
        if (StringUtils.hasText(searchVO.getUserId())) {
            sql.append(" AND USER_ID = ? ");
            params.add(searchVO.getUserId());
        }
        if (StringUtils.hasText(searchVO.getKeyword())) {
            sql.append(" AND (DEPT_NM LIKE ? OR TASK_NM LIKE ? OR HOST_NM LIKE ? OR ORIGINAL_FILE_NM LIKE ? OR USER_ID LIKE ?) ");
            String like = "%" + searchVO.getKeyword() + "%";
            for (int i = 0; i < 5; i++) {
                params.add(like);
            }
        }
    }

    @Override
    public int countActiveUploadsByUser(String userId) {
        String sql = "SELECT COUNT(*) FROM TB_ADA_DUMP WHERE USER_ID = ? AND STATUS_CD IN "
                + "('UPLOAD_READY', 'UPLOADING', 'VALIDATING', 'CANCEL_REQUESTED', 'FINALIZING', 'CLEANUP_FAILED', 'CLEANUP_PENDING')";
        Integer count = jdbcTemplate.queryForObject(sql, new Object[] { userId }, Integer.class);
        return count == null ? 0 : count;
    }

    @Override
    public List<DumpVO> selectExpiredCandidates(Timestamp cutoff, int limit) {
        String sql = "SELECT DUMP_ID, USER_ID, DUMP_TYPE, DEPT_NM, TASK_NM, HOST_NM, ORIGINAL_FILE_NM, STORED_FILE_NM, "
                + "FILE_SIZE, STATUS_CD, ANALYSIS_STATUS_CD, FAIL_REASON, EXPIRED_YN, "
                + "TO_CHAR(REG_DT, 'YYYY-MM-DD HH24:MI:SS') AS REG_DT, 1 AS EXPIRED_NOW "
                + "FROM TB_ADA_DUMP WHERE STATUS_CD = 'STORED' AND EXPIRED_YN = 'N' AND REG_DT < ? "
                + "ORDER BY REG_DT ASC LIMIT ?";
        return jdbcTemplate.query(sql, new Object[] { cutoff, limit }, new DumpRowMapper());
    }

    @Override
    public void markCleaned(String dumpId) {
        String sql = "UPDATE TB_ADA_DUMP SET STATUS_CD = 'EXPIRED', EXPIRED_YN = 'Y', CLEANED_DT = CURRENT_DATETIME, "
                + "MOD_DT = CURRENT_DATETIME WHERE DUMP_ID = ?";
        jdbcTemplate.update(sql, dumpId);
    }

    @Override
    public void updateAnalysisStatus(String dumpId, String analysisStatusCd) {
        jdbcTemplate.update("UPDATE TB_ADA_DUMP SET ANALYSIS_STATUS_CD = ?, MOD_DT = CURRENT_DATETIME WHERE DUMP_ID = ?",
                analysisStatusCd, dumpId);
    }

    private static class DumpRowMapper implements RowMapper<DumpVO> {
        @Override
        public DumpVO mapRow(ResultSet rs, int rowNum) throws SQLException {
            DumpVO vo = new DumpVO();
            vo.setDumpId(rs.getString("DUMP_ID"));
            vo.setUserId(rs.getString("USER_ID"));
            vo.setDumpType(rs.getString("DUMP_TYPE"));
            vo.setDeptNm(rs.getString("DEPT_NM"));
            vo.setTaskNm(rs.getString("TASK_NM"));
            vo.setHostNm(rs.getString("HOST_NM"));
            vo.setOriginalFileNm(rs.getString("ORIGINAL_FILE_NM"));
            vo.setStoredFileNm(rs.getString("STORED_FILE_NM"));
            vo.setFileSize(rs.getLong("FILE_SIZE"));
            vo.setStatusCd(rs.getString("STATUS_CD"));
            vo.setAnalysisStatusCd(rs.getString("ANALYSIS_STATUS_CD"));
            vo.setFailReason(rs.getString("FAIL_REASON"));
            vo.setExpiredYn(rs.getString("EXPIRED_YN"));
            vo.setRegDt(rs.getString("REG_DT"));
            vo.setExpiredNow(rs.getInt("EXPIRED_NOW") == 1);
            return vo;
        }
    }
}

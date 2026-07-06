package com.aonfine.board.service.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Resource;
import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import com.aonfine.board.service.BoardSearchVO;
import com.aonfine.board.service.BoardVO;

@Repository("boardMapper")
public class BoardJdbcMapper implements BoardMapper {

    private JdbcTemplate jdbcTemplate;

    @Resource(name = "dataSource")
    public void setDataSource(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public List<BoardVO> selectBoardList(BoardSearchVO searchVO) {
        List<Object> params = new ArrayList<Object>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT BOARD_ID, BOARD_TYPE, TITLE, CONTENT, WRITER_ID, WRITER_NM, VIEW_CNT, USE_YN, ");
        sql.append("TO_CHAR(REG_DT, 'YYYY-MM-DD HH24:MI:SS') AS REG_DT, ");
        sql.append("TO_CHAR(MOD_DT, 'YYYY-MM-DD HH24:MI:SS') AS MOD_DT ");
        sql.append("FROM TB_BOARD WHERE USE_YN = 'Y' ");
        appendSearchCondition(sql, params, searchVO);
        sql.append("ORDER BY REG_DT DESC LIMIT ?, ?");
        params.add(searchVO.getFirstIndex());
        params.add(searchVO.getRecordCountPerPage());
        return jdbcTemplate.query(sql.toString(), params.toArray(), new BoardRowMapper());
    }

    public int selectBoardListTotCnt(BoardSearchVO searchVO) {
        List<Object> params = new ArrayList<Object>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COUNT(*) FROM TB_BOARD WHERE USE_YN = 'Y' ");
        appendSearchCondition(sql, params, searchVO);
        Integer count = jdbcTemplate.queryForObject(sql.toString(), params.toArray(), Integer.class);
        return count == null ? 0 : count.intValue();
    }

    public BoardVO selectBoard(Integer boardId) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT BOARD_ID, BOARD_TYPE, TITLE, CONTENT, WRITER_ID, WRITER_NM, VIEW_CNT, USE_YN, ");
        sql.append("TO_CHAR(REG_DT, 'YYYY-MM-DD HH24:MI:SS') AS REG_DT, ");
        sql.append("TO_CHAR(MOD_DT, 'YYYY-MM-DD HH24:MI:SS') AS MOD_DT ");
        sql.append("FROM TB_BOARD WHERE BOARD_ID = ? AND USE_YN = 'Y'");
        List<BoardVO> list = jdbcTemplate.query(sql.toString(), new Object[] { boardId }, new BoardRowMapper());
        return list.isEmpty() ? null : list.get(0);
    }

    public void increaseViewCount(Integer boardId) {
        jdbcTemplate.update("UPDATE TB_BOARD SET VIEW_CNT = VIEW_CNT + 1 WHERE BOARD_ID = ? AND USE_YN = 'Y'", boardId);
    }

    public void insertBoard(BoardVO boardVO) {
        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO TB_BOARD (BOARD_TYPE, TITLE, CONTENT, WRITER_ID, WRITER_NM, VIEW_CNT, USE_YN, REG_DT) ");
        sql.append("VALUES (?, ?, ?, ?, ?, 0, 'Y', CURRENT_DATETIME)");
        jdbcTemplate.update(sql.toString(), ps -> {
            ps.setString(1, boardVO.getBoardType());
            ps.setString(2, boardVO.getTitle());
            ps.setString(3, boardVO.getContent());
            ps.setString(4, boardVO.getWriterId());
            ps.setString(5, boardVO.getWriterName());
        });
    }

    public void updateBoard(BoardVO boardVO) {
        StringBuilder sql = new StringBuilder();
        sql.append("UPDATE TB_BOARD SET TITLE = ?, CONTENT = ?, MOD_DT = CURRENT_DATETIME ");
        sql.append("WHERE BOARD_ID = ? AND USE_YN = 'Y'");
        jdbcTemplate.update(sql.toString(), boardVO.getTitle(), boardVO.getContent(), boardVO.getBoardId());
    }

    public void deleteBoard(Integer boardId) {
        jdbcTemplate.update("UPDATE TB_BOARD SET USE_YN = 'N', MOD_DT = CURRENT_DATETIME WHERE BOARD_ID = ?", boardId);
    }

    private void appendSearchCondition(StringBuilder sql, List<Object> params, BoardSearchVO searchVO) {
        if (searchVO == null) {
            return;
        }
        if (StringUtils.hasText(searchVO.getBoardType())) {
            sql.append("AND BOARD_TYPE = ? ");
            params.add(searchVO.getBoardType());
        }
        if (StringUtils.hasText(searchVO.getSearchKeyword())) {
            sql.append("AND (LOWER(TITLE) LIKE ? OR LOWER(CONTENT) LIKE ?) ");
            String keyword = "%" + searchVO.getSearchKeyword().toLowerCase() + "%";
            params.add(keyword);
            params.add(keyword);
        }
    }

    private static class BoardRowMapper implements RowMapper<BoardVO> {
        public BoardVO mapRow(ResultSet rs, int rowNum) throws SQLException {
            BoardVO vo = new BoardVO();
            vo.setBoardId(Integer.valueOf(rs.getInt("BOARD_ID")));
            vo.setBoardType(rs.getString("BOARD_TYPE"));
            vo.setTitle(rs.getString("TITLE"));
            vo.setContent(rs.getString("CONTENT"));
            vo.setWriterId(rs.getString("WRITER_ID"));
            vo.setWriterName(rs.getString("WRITER_NM"));
            vo.setViewCount(Integer.valueOf(rs.getInt("VIEW_CNT")));
            vo.setUseYn(rs.getString("USE_YN"));
            vo.setRegDt(rs.getString("REG_DT"));
            vo.setModDt(rs.getString("MOD_DT"));
            return vo;
        }
    }
}
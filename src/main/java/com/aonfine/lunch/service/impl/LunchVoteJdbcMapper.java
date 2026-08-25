package com.aonfine.lunch.service.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import javax.annotation.Resource;
import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.aonfine.lunch.service.LunchVoteVO;

@Repository("lunchVoteMapper")
public class LunchVoteJdbcMapper implements LunchVoteMapper {

    private JdbcTemplate jdbcTemplate;

    @Resource(name = "dataSource")
    public void setDataSource(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public List<LunchVoteVO> selectTodayTopList(int limit) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT r.RESTAURANT_ID, r.RESTAURANT_NM, r.STORE_NM, r.MENU_NM, r.ADDRESS, r.IMAGE_PATH, ");
        sql.append("COUNT(v.VOTE_ID) AS VOTE_COUNT ");
        sql.append("FROM TB_LUNCH_VOTE v ");
        sql.append("INNER JOIN TB_RESTAURANT r ON v.RESTAURANT_ID = r.RESTAURANT_ID AND r.DEL_YN = 'N' ");
        sql.append("WHERE v.VOTE_DT = CURRENT_DATE ");
        sql.append("GROUP BY r.RESTAURANT_ID, r.RESTAURANT_NM, r.STORE_NM, r.MENU_NM, r.ADDRESS, r.IMAGE_PATH ");
        sql.append("ORDER BY VOTE_COUNT DESC, r.STORE_NM ASC LIMIT 0, ?");
        return jdbcTemplate.query(sql.toString(), new Object[] { limit }, new LunchVoteRowMapper(false));
    }

    public List<LunchVoteVO> selectTodayBoardList() {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT r.RESTAURANT_ID, r.RESTAURANT_NM, r.STORE_NM, r.MENU_NM, r.ADDRESS, r.IMAGE_PATH, ");
        sql.append("COUNT(v.VOTE_ID) AS VOTE_COUNT ");
        sql.append("FROM TB_RESTAURANT r ");
        sql.append("LEFT JOIN TB_LUNCH_VOTE v ON r.RESTAURANT_ID = v.RESTAURANT_ID AND v.VOTE_DT = CURRENT_DATE ");
        sql.append("WHERE r.DEL_YN = 'N' ");
        sql.append("GROUP BY r.RESTAURANT_ID, r.RESTAURANT_NM, r.STORE_NM, r.MENU_NM, r.ADDRESS, r.IMAGE_PATH ");
        sql.append("ORDER BY VOTE_COUNT DESC, r.STORE_NM ASC");
        return jdbcTemplate.query(sql.toString(), new LunchVoteRowMapper(false));
    }

    public List<LunchVoteVO> selectCandidateList() {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT r.RESTAURANT_ID, r.RESTAURANT_NM, r.STORE_NM, r.MENU_NM, r.ADDRESS, r.IMAGE_PATH, ");
        sql.append("0 AS VOTE_COUNT ");
        sql.append("FROM TB_RESTAURANT r WHERE r.DEL_YN = 'N' ORDER BY r.RESTAURANT_ID");
        return jdbcTemplate.query(sql.toString(), new LunchVoteRowMapper(false));
    }

    public LunchVoteVO selectTodayMyVote(String userId) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT v.VOTE_ID, TO_CHAR(v.VOTE_DT, 'YYYY-MM-DD') AS VOTE_DT, v.USER_ID, v.USER_NM, ");
        sql.append("r.RESTAURANT_ID, r.RESTAURANT_NM, r.STORE_NM, r.MENU_NM, r.ADDRESS, r.IMAGE_PATH, ");
        sql.append("0 AS VOTE_COUNT, TO_CHAR(v.REG_DT, 'YYYY-MM-DD HH24:MI:SS') AS REG_DT ");
        sql.append("FROM TB_LUNCH_VOTE v ");
        sql.append("INNER JOIN TB_RESTAURANT r ON v.RESTAURANT_ID = r.RESTAURANT_ID ");
        sql.append("WHERE v.VOTE_DT = CURRENT_DATE AND v.USER_ID = ?");
        List<LunchVoteVO> list = jdbcTemplate.query(sql.toString(), new Object[] { userId }, new LunchVoteRowMapper(true));
        return list.isEmpty() ? null : list.get(0);
    }

    public LunchVoteVO selectRestaurant(Integer restaurantId) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT r.RESTAURANT_ID, r.RESTAURANT_NM, r.STORE_NM, r.MENU_NM, r.ADDRESS, r.IMAGE_PATH, ");
        sql.append("0 AS VOTE_COUNT ");
        sql.append("FROM TB_RESTAURANT r WHERE r.RESTAURANT_ID = ? AND r.DEL_YN = 'N'");
        List<LunchVoteVO> list = jdbcTemplate.query(sql.toString(), new Object[] { restaurantId }, new LunchVoteRowMapper(false));
        return list.isEmpty() ? null : list.get(0);
    }

    public void insertVote(LunchVoteVO voteVO) {
        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO TB_LUNCH_VOTE (VOTE_DT, USER_ID, USER_NM, RESTAURANT_ID, REG_DT) ");
        sql.append("VALUES (CURRENT_DATE, ?, ?, ?, CURRENT_DATETIME)");
        jdbcTemplate.update(sql.toString(), ps -> {
            ps.setString(1, voteVO.getUserId());
            ps.setString(2, voteVO.getUserName());
            ps.setInt(3, voteVO.getRestaurantId().intValue());
        });
    }
    public int deleteTodayVote(String userId) {
        StringBuilder sql = new StringBuilder();
        sql.append("DELETE FROM TB_LUNCH_VOTE ");
        sql.append("WHERE VOTE_DT = CURRENT_DATE AND USER_ID = ?");
        return jdbcTemplate.update(sql.toString(), userId);
    }

    private static class LunchVoteRowMapper implements RowMapper<LunchVoteVO> {
        private final boolean includeVoteInfo;

        LunchVoteRowMapper(boolean includeVoteInfo) {
            this.includeVoteInfo = includeVoteInfo;
        }

        public LunchVoteVO mapRow(ResultSet rs, int rowNum) throws SQLException {
            LunchVoteVO vo = new LunchVoteVO();
            if (includeVoteInfo) {
                vo.setVoteId(Integer.valueOf(rs.getInt("VOTE_ID")));
                vo.setVoteDate(rs.getString("VOTE_DT"));
                vo.setUserId(rs.getString("USER_ID"));
                vo.setUserName(rs.getString("USER_NM"));
                vo.setRegDt(rs.getString("REG_DT"));
            }
            vo.setRestaurantId(Integer.valueOf(rs.getInt("RESTAURANT_ID")));
            vo.setRestaurantName(rs.getString("RESTAURANT_NM"));
            vo.setStoreName(rs.getString("STORE_NM"));
            vo.setMenuName(rs.getString("MENU_NM"));
            vo.setAddress(rs.getString("ADDRESS"));
            vo.setImagePath(rs.getString("IMAGE_PATH"));
            vo.setVoteCount(Integer.valueOf(rs.getInt("VOTE_COUNT")));
            return vo;
        }
    }
}
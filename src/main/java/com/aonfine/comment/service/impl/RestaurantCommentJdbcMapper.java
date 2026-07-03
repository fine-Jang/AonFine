package com.aonfine.comment.service.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import javax.annotation.Resource;
import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.aonfine.comment.service.RestaurantCommentVO;

@Repository("restaurantCommentMapper")
public class RestaurantCommentJdbcMapper implements RestaurantCommentMapper {

    private JdbcTemplate jdbcTemplate;

    @Resource(name = "dataSource")
    public void setDataSource(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public List<RestaurantCommentVO> selectCommentList(Integer restaurantId) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COMMENT_ID, RESTAURANT_ID, USER_ID, USER_NM, COMMENT_TEXT, ");
        sql.append("TO_CHAR(REG_DT, 'YYYY-MM-DD HH24:MI:SS') AS REG_DT, ");
        sql.append("TO_CHAR(MOD_DT, 'YYYY-MM-DD HH24:MI:SS') AS MOD_DT ");
        sql.append("FROM TB_RESTAURANT_COMMENT ");
        sql.append("WHERE RESTAURANT_ID = ? AND DEL_YN = 'N' ");
        sql.append("ORDER BY REG_DT DESC");
        return jdbcTemplate.query(sql.toString(), new Object[] { restaurantId }, new RestaurantCommentRowMapper());
    }

    public void insertComment(RestaurantCommentVO commentVO) {
        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO TB_RESTAURANT_COMMENT ");
        sql.append("(RESTAURANT_ID, USER_ID, USER_NM, COMMENT_TEXT, REG_DT) ");
        sql.append("VALUES (?, ?, ?, ?, CURRENT_DATETIME)");
        jdbcTemplate.update(sql.toString(), ps -> {
            ps.setInt(1, commentVO.getRestaurantId().intValue());
            ps.setString(2, commentVO.getUserId());
            ps.setString(3, commentVO.getUserName());
            ps.setString(4, commentVO.getCommentText());
        });
    }

    public void deleteComment(Integer commentId, String userId) {
        StringBuilder sql = new StringBuilder();
        sql.append("UPDATE TB_RESTAURANT_COMMENT ");
        sql.append("SET DEL_YN = 'Y', MOD_DT = CURRENT_DATETIME ");
        sql.append("WHERE COMMENT_ID = ? AND USER_ID = ? AND DEL_YN = 'N'");
        jdbcTemplate.update(sql.toString(), commentId, userId);
    }

    private static class RestaurantCommentRowMapper implements RowMapper<RestaurantCommentVO> {
        public RestaurantCommentVO mapRow(ResultSet rs, int rowNum) throws SQLException {
            RestaurantCommentVO vo = new RestaurantCommentVO();
            vo.setCommentId(Integer.valueOf(rs.getInt("COMMENT_ID")));
            vo.setRestaurantId(Integer.valueOf(rs.getInt("RESTAURANT_ID")));
            vo.setUserId(rs.getString("USER_ID"));
            vo.setUserName(rs.getString("USER_NM"));
            vo.setCommentText(rs.getString("COMMENT_TEXT"));
            vo.setRegDt(rs.getString("REG_DT"));
            vo.setModDt(rs.getString("MOD_DT"));
            return vo;
        }
    }
}
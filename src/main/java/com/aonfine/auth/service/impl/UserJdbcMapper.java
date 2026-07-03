package com.aonfine.auth.service.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

import javax.annotation.Resource;
import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import com.aonfine.auth.service.UserVO;

@Repository("userMapper")
public class UserJdbcMapper implements UserMapper {

    private JdbcTemplate jdbcTemplate;

    @Resource(name = "dataSource")
    public void setDataSource(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public UserVO selectUser(String userId) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT USER_ID, PASSWORD_HASH, USER_NM, PHONE_NO, ");
        sql.append("TO_CHAR(REG_DT, 'YYYY-MM-DD HH24:MI:SS') AS REG_DT ");
        sql.append("FROM TB_USER WHERE USER_ID = ?");
        List<UserVO> list = jdbcTemplate.query(sql.toString(), new Object[] { userId }, new UserRowMapper());
        return list.isEmpty() ? null : list.get(0);
    }

    public void insertUser(UserVO userVO) {
        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO TB_USER (USER_ID, PASSWORD_HASH, USER_NM, PHONE_NO, REG_DT) ");
        sql.append("VALUES (?, ?, ?, ?, CURRENT_DATETIME)");
        jdbcTemplate.update(sql.toString(), ps -> {
            ps.setString(1, userVO.getUserId());
            ps.setString(2, userVO.getPasswordHash());
            ps.setString(3, userVO.getUserName());
            if (StringUtils.hasText(userVO.getPhoneNo())) {
                ps.setString(4, userVO.getPhoneNo());
            } else {
                ps.setNull(4, Types.VARCHAR);
            }
        });
    }

    private static class UserRowMapper implements RowMapper<UserVO> {
        public UserVO mapRow(ResultSet rs, int rowNum) throws SQLException {
            UserVO vo = new UserVO();
            vo.setUserId(rs.getString("USER_ID"));
            vo.setPasswordHash(rs.getString("PASSWORD_HASH"));
            vo.setUserName(rs.getString("USER_NM"));
            vo.setPhoneNo(rs.getString("PHONE_NO"));
            vo.setRegDt(rs.getString("REG_DT"));
            return vo;
        }
    }
}

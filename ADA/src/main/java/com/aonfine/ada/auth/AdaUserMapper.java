package com.aonfine.ada.auth;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import javax.annotation.Resource;
import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * TB_USER 읽기 전용 접근. AonFine의 UserJdbcMapper와 같은 컬럼을 조회하여 해시 비교가
 * 호환되도록 유지한다. INSERT/UPDATE/DELETE는 이 클래스에 존재하지 않는다(의도적).
 */
@Repository("adaUserMapper")
public class AdaUserMapper {

    private JdbcTemplate jdbcTemplate;

    @Resource(name = "dataSource")
    public void setDataSource(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public AdaUserVO selectUser(String userId) {
        String sql = "SELECT USER_ID, PASSWORD_HASH, USER_NM, ROLE_CD FROM TB_USER WHERE USER_ID = ?";
        List<AdaUserVO> list = jdbcTemplate.query(sql, new Object[] { userId }, new RowMapper<AdaUserVO>() {
            @Override
            public AdaUserVO mapRow(ResultSet rs, int rowNum) throws SQLException {
                AdaUserVO vo = new AdaUserVO();
                vo.setUserId(rs.getString("USER_ID"));
                vo.setPasswordHash(rs.getString("PASSWORD_HASH"));
                vo.setUserName(rs.getString("USER_NM"));
                vo.setRoleCode(rs.getString("ROLE_CD"));
                return vo;
            }
        });
        return list.isEmpty() ? null : list.get(0);
    }
}

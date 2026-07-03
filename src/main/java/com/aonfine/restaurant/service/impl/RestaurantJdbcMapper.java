package com.aonfine.restaurant.service.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Resource;
import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import com.aonfine.restaurant.service.RestaurantSearchVO;
import com.aonfine.restaurant.service.RestaurantVO;

@Repository("restaurantMapper")
public class RestaurantJdbcMapper implements RestaurantMapper {

    private JdbcTemplate jdbcTemplate;

    @Resource(name = "dataSource")
    public void setDataSource(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public List<RestaurantVO> selectRestaurantList(RestaurantSearchVO searchVO) {
        List<Object> params = new ArrayList<Object>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT RESTAURANT_ID, RESTAURANT_NM, STORE_NM, IMAGE_PATH, IMAGE_ORG_NM, ");
        sql.append("MENU_NM, CATEGORY_CD, ADDRESS, LATITUDE, LONGITUDE, DESCRIPTION, ");
        sql.append("TO_CHAR(REG_DT, 'YYYY-MM-DD HH24:MI:SS') AS REG_DT, ");
        sql.append("TO_CHAR(MOD_DT, 'YYYY-MM-DD HH24:MI:SS') AS MOD_DT ");
        sql.append("FROM TB_RESTAURANT WHERE DEL_YN = 'N' ");
        appendSearchCondition(sql, params, searchVO);
        sql.append("ORDER BY REG_DT DESC ");
        sql.append("LIMIT ?, ?");
        params.add(searchVO.getFirstIndex());
        params.add(searchVO.getRecordCountPerPage());
        return jdbcTemplate.query(sql.toString(), params.toArray(), new RestaurantRowMapper());
    }

    public int selectRestaurantListTotCnt(RestaurantSearchVO searchVO) {
        List<Object> params = new ArrayList<Object>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COUNT(*) FROM TB_RESTAURANT WHERE DEL_YN = 'N' ");
        appendSearchCondition(sql, params, searchVO);
        Integer count = jdbcTemplate.queryForObject(sql.toString(), params.toArray(), Integer.class);
        return count == null ? 0 : count.intValue();
    }

    public List<RestaurantVO> selectLatestRestaurantList(int limit) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT RESTAURANT_ID, RESTAURANT_NM, STORE_NM, IMAGE_PATH, IMAGE_ORG_NM, ");
        sql.append("MENU_NM, CATEGORY_CD, ADDRESS, LATITUDE, LONGITUDE, DESCRIPTION, ");
        sql.append("TO_CHAR(REG_DT, 'YYYY-MM-DD HH24:MI:SS') AS REG_DT, ");
        sql.append("TO_CHAR(MOD_DT, 'YYYY-MM-DD HH24:MI:SS') AS MOD_DT ");
        sql.append("FROM TB_RESTAURANT WHERE DEL_YN = 'N' ");
        sql.append("ORDER BY REG_DT DESC LIMIT 0, ?");
        return jdbcTemplate.query(sql.toString(), new Object[] { limit }, new RestaurantRowMapper());
    }

    public RestaurantVO selectRestaurant(Integer restaurantId) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT RESTAURANT_ID, RESTAURANT_NM, STORE_NM, IMAGE_PATH, IMAGE_ORG_NM, ");
        sql.append("MENU_NM, CATEGORY_CD, ADDRESS, LATITUDE, LONGITUDE, DESCRIPTION, ");
        sql.append("TO_CHAR(REG_DT, 'YYYY-MM-DD HH24:MI:SS') AS REG_DT, ");
        sql.append("TO_CHAR(MOD_DT, 'YYYY-MM-DD HH24:MI:SS') AS MOD_DT ");
        sql.append("FROM TB_RESTAURANT WHERE RESTAURANT_ID = ? AND DEL_YN = 'N'");
        List<RestaurantVO> list = jdbcTemplate.query(sql.toString(), new Object[] { restaurantId }, new RestaurantRowMapper());
        return list.isEmpty() ? null : list.get(0);
    }

    public void insertRestaurant(RestaurantVO restaurantVO) {
        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO TB_RESTAURANT ");
        sql.append("(RESTAURANT_NM, STORE_NM, IMAGE_PATH, IMAGE_ORG_NM, MENU_NM, CATEGORY_CD, ");
        sql.append("ADDRESS, LATITUDE, LONGITUDE, DESCRIPTION, REG_DT) ");
        sql.append("VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_DATETIME)");
        jdbcTemplate.update(sql.toString(), ps -> {
            ps.setString(1, restaurantVO.getRestaurantName());
            ps.setString(2, restaurantVO.getStoreName());
            setNullableString(ps, 3, restaurantVO.getImagePath());
            setNullableString(ps, 4, restaurantVO.getImageOriginalName());
            ps.setString(5, restaurantVO.getMenuName());
            ps.setString(6, restaurantVO.getCategoryCode());
            ps.setString(7, restaurantVO.getAddress());
            setNullableBigDecimal(ps, 8, restaurantVO.getLatitude());
            setNullableBigDecimal(ps, 9, restaurantVO.getLongitude());
            setNullableString(ps, 10, restaurantVO.getDescription());
        });
    }

    public void updateRestaurant(RestaurantVO restaurantVO) {
        StringBuilder sql = new StringBuilder();
        List<Object> params = new ArrayList<Object>();
        sql.append("UPDATE TB_RESTAURANT SET ");
        sql.append("RESTAURANT_NM = ?, STORE_NM = ?, MENU_NM = ?, CATEGORY_CD = ?, ");
        sql.append("ADDRESS = ?, LATITUDE = ?, LONGITUDE = ?, DESCRIPTION = ?, MOD_DT = CURRENT_DATETIME ");
        params.add(restaurantVO.getRestaurantName());
        params.add(restaurantVO.getStoreName());
        params.add(restaurantVO.getMenuName());
        params.add(restaurantVO.getCategoryCode());
        params.add(restaurantVO.getAddress());
        params.add(restaurantVO.getLatitude());
        params.add(restaurantVO.getLongitude());
        params.add(restaurantVO.getDescription());
        if (StringUtils.hasText(restaurantVO.getImagePath())) {
            sql.append(", IMAGE_PATH = ?, IMAGE_ORG_NM = ? ");
            params.add(restaurantVO.getImagePath());
            params.add(restaurantVO.getImageOriginalName());
        }
        sql.append("WHERE RESTAURANT_ID = ? AND DEL_YN = 'N'");
        params.add(restaurantVO.getRestaurantId());
        jdbcTemplate.update(sql.toString(), ps -> {
            int index = 1;
            ps.setString(index++, restaurantVO.getRestaurantName());
            ps.setString(index++, restaurantVO.getStoreName());
            ps.setString(index++, restaurantVO.getMenuName());
            ps.setString(index++, restaurantVO.getCategoryCode());
            ps.setString(index++, restaurantVO.getAddress());
            setNullableBigDecimal(ps, index++, restaurantVO.getLatitude());
            setNullableBigDecimal(ps, index++, restaurantVO.getLongitude());
            setNullableString(ps, index++, restaurantVO.getDescription());
            if (StringUtils.hasText(restaurantVO.getImagePath())) {
                ps.setString(index++, restaurantVO.getImagePath());
                setNullableString(ps, index++, restaurantVO.getImageOriginalName());
            }
            ps.setInt(index, restaurantVO.getRestaurantId().intValue());
        });
    }

    public void deleteRestaurant(Integer restaurantId) {
        jdbcTemplate.update("UPDATE TB_RESTAURANT SET DEL_YN = 'Y', MOD_DT = CURRENT_DATETIME WHERE RESTAURANT_ID = ?",
                restaurantId);
    }

    private void appendSearchCondition(StringBuilder sql, List<Object> params, RestaurantSearchVO searchVO) {
        if (searchVO == null) {
            return;
        }
        if (StringUtils.hasText(searchVO.getCategoryCode())) {
            sql.append("AND CATEGORY_CD = ? ");
            params.add(searchVO.getCategoryCode());
        }
        if (StringUtils.hasText(searchVO.getSearchKeyword())) {
            sql.append("AND (LOWER(RESTAURANT_NM) LIKE ? OR LOWER(STORE_NM) LIKE ? OR LOWER(MENU_NM) LIKE ?) ");
            String keyword = "%" + searchVO.getSearchKeyword().toLowerCase() + "%";
            params.add(keyword);
            params.add(keyword);
            params.add(keyword);
        }
    }

    private void setNullableString(java.sql.PreparedStatement ps, int index, String value) throws SQLException {
        if (StringUtils.hasText(value)) {
            ps.setString(index, value);
        } else {
            ps.setNull(index, Types.VARCHAR);
        }
    }

    private void setNullableBigDecimal(java.sql.PreparedStatement ps, int index, java.math.BigDecimal value)
            throws SQLException {
        if (value != null) {
            ps.setBigDecimal(index, value);
        } else {
            ps.setNull(index, Types.NUMERIC);
        }
    }

    private static class RestaurantRowMapper implements RowMapper<RestaurantVO> {
        public RestaurantVO mapRow(ResultSet rs, int rowNum) throws SQLException {
            RestaurantVO vo = new RestaurantVO();
            vo.setRestaurantId(Integer.valueOf(rs.getInt("RESTAURANT_ID")));
            vo.setRestaurantName(rs.getString("RESTAURANT_NM"));
            vo.setStoreName(rs.getString("STORE_NM"));
            vo.setImagePath(rs.getString("IMAGE_PATH"));
            vo.setImageOriginalName(rs.getString("IMAGE_ORG_NM"));
            vo.setMenuName(rs.getString("MENU_NM"));
            vo.setCategoryCode(rs.getString("CATEGORY_CD"));
            vo.setAddress(rs.getString("ADDRESS"));
            vo.setLatitude(rs.getBigDecimal("LATITUDE"));
            vo.setLongitude(rs.getBigDecimal("LONGITUDE"));
            vo.setDescription(rs.getString("DESCRIPTION"));
            vo.setRegDt(rs.getString("REG_DT"));
            vo.setModDt(rs.getString("MOD_DT"));
            return vo;
        }
    }
}

package com.aonfine.common.db;

import javax.annotation.Resource;
import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service("dbTestService")
public class DbTestServiceImpl implements DbTestService {

    @Resource(name = "dataSource")
    private DataSource dataSource;

    public int selectOne() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Integer result = jdbcTemplate.queryForObject("select 1", Integer.class);
        return result == null ? 0 : result.intValue();
    }
}

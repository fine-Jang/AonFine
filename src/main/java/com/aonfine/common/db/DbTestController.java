package com.aonfine.common.db;

import javax.annotation.Resource;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class DbTestController {

    @Resource(name = "dbTestService")
    private DbTestService dbTestService;

    @ResponseBody
    @RequestMapping(value = "/dbTest.do", produces = "text/plain; charset=UTF-8")
    public String dbTest() {
        int result = dbTestService.selectOne();
        return "DB 연결 성공: select 1 = " + result;
    }
}

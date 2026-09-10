package com.aonfine.ada.dump.web;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.aonfine.ada.auth.AdaSessionConstants;
import com.aonfine.ada.auth.AdaUserVO;
import com.aonfine.ada.dump.DumpSearchVO;
import com.aonfine.ada.dump.DumpService;

@Controller
@RequestMapping("/dump")
public class DumpListController {

    @Resource(name = "dumpService")
    private DumpService dumpService;

    @RequestMapping("/list.do")
    public String list(@RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "pageIndex", defaultValue = "1") int pageIndex,
            HttpSession session, Model model) {
        AdaUserVO user = (AdaUserVO) session.getAttribute(AdaSessionConstants.LOGIN_SESSION_KEY);

        DumpSearchVO searchVO = new DumpSearchVO();
        searchVO.setKeyword(keyword);
        searchVO.setPageIndex(pageIndex);

        DumpService.PageResult result = dumpService.list(searchVO, user.isAdmin(), user.getUserId());

        model.addAttribute("loginUser", user);
        model.addAttribute("csrfToken", com.aonfine.ada.auth.AdaRequestSecurity.token(session));
        model.addAttribute("dumpList", result.items);
        model.addAttribute("totalCount", result.totalCount);
        model.addAttribute("pageIndex", searchVO.getPageIndex());
        model.addAttribute("pageSize", searchVO.getPageSize());
        model.addAttribute("keyword", keyword);
        model.addAttribute("totalPages",
                (int) Math.ceil(result.totalCount / (double) Math.max(1, searchVO.getPageSize())));
        return "dump/list";
    }
}

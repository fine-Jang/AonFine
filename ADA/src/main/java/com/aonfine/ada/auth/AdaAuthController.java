package com.aonfine.ada.auth;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * ADA 로그인/로그아웃. 회원가입 기능은 의도적으로 없다(요구사항: 회원가입 없음).
 */
@Controller
public class AdaAuthController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdaAuthController.class);

    @Resource(name = "adaAuthService")
    private AdaAuthService adaAuthService;

    @RequestMapping("/login.do")
    public String loginForm() {
        return "auth/login";
    }

    @RequestMapping("/loginProcess.do")
    public String login(@RequestParam("userId") String userId,
            @RequestParam("password") String password,
            HttpServletRequest request,
            Model model) {
        AdaUserVO user = adaAuthService.login(userId, password);
        if (user == null) {
            LOGGER.info("ADA login failed userId={}", userId);
            model.addAttribute("message", "아이디 또는 비밀번호가 올바르지 않습니다.");
            model.addAttribute("userId", userId);
            return "auth/login";
        }
        request.getSession(true).setAttribute(AdaSessionConstants.LOGIN_SESSION_KEY, user);
        LOGGER.info("ADA login success userId={}", user.getUserId());
        return "redirect:/dump/list.do";
    }

    @RequestMapping("/logout.do")
    public String logout(HttpSession session) {
        if (session != null) {
            session.invalidate();
        }
        return "redirect:/login.do";
    }
}

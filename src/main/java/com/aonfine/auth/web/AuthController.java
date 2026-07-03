package com.aonfine.auth.web;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.aonfine.auth.service.UserService;
import com.aonfine.auth.service.UserVO;

@Controller
public class AuthController {

    public static final String LOGIN_SESSION_KEY = "loginUser";

    @Resource(name = "userService")
    private UserService userService;

    @RequestMapping("/login.do")
    public String loginForm(@RequestParam(value = "returnUrl", required = false) String returnUrl, Model model) {
        model.addAttribute("returnUrl", returnUrl);
        return "auth/login";
    }

    @RequestMapping("/loginProcess.do")
    public String login(@RequestParam("userId") String userId,
            @RequestParam("password") String password,
            @RequestParam(value = "returnUrl", required = false) String returnUrl,
            HttpServletRequest request,
            Model model) {
        UserVO loginUser = userService.login(userId, password);
        if (loginUser == null) {
            model.addAttribute("message", "아이디 또는 패스워드가 올바르지 않습니다.");
            model.addAttribute("userId", userId);
            model.addAttribute("returnUrl", returnUrl);
            return "auth/login";
        }

        request.getSession(true).setAttribute(LOGIN_SESSION_KEY, loginUser);
        if (isSafeReturnUrl(returnUrl)) {
            return "redirect:" + returnUrl;
        }
        return "redirect:/main.do";
    }

    @RequestMapping("/join.do")
    public String joinForm(Model model) {
        model.addAttribute("user", new UserVO());
        return "auth/join";
    }

    @RequestMapping("/joinProcess.do")
    public String join(@ModelAttribute("user") UserVO userVO,
            RedirectAttributes redirectAttributes,
            Model model) {
        try {
            userService.join(userVO);
        } catch (IllegalArgumentException e) {
            model.addAttribute("message", e.getMessage());
            return "auth/join";
        }
        redirectAttributes.addFlashAttribute("message", "회원가입이 완료되었습니다. 로그인해 주세요.");
        return "redirect:/login.do";
    }

    @RequestMapping("/logout.do")
    public String logout(HttpSession session) {
        if (session != null) {
            session.invalidate();
        }
        return "redirect:/main.do";
    }

    private boolean isSafeReturnUrl(String returnUrl) {
        return StringUtils.hasText(returnUrl) && returnUrl.startsWith("/") && !returnUrl.startsWith("//")
                && !returnUrl.contains("://");
    }
}

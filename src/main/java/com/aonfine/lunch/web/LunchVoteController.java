package com.aonfine.lunch.web;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.aonfine.auth.service.UserVO;
import com.aonfine.auth.web.AuthController;
import com.aonfine.lunch.service.LunchVoteService;
import com.aonfine.lunch.service.LunchVoteVO;

@Controller
public class LunchVoteController {

    @Resource(name = "lunchVoteService")
    private LunchVoteService lunchVoteService;

    @RequestMapping("/lunch/today.do")
    public String today(HttpSession session, Model model) {
        UserVO loginUser = getLoginUser(session);
        model.addAttribute("todayVoteList", lunchVoteService.selectTodayBoardList());
        if (loginUser != null) {
            model.addAttribute("myLunchVote", lunchVoteService.selectTodayMyVote(loginUser.getUserId()));
        }
        return "lunch/today";
    }

    @RequestMapping("/lunch/vote.do")
    public String vote(@RequestParam("restaurantId") Integer restaurantId,
            @RequestParam(value = "returnUrl", required = false) String returnUrl,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        UserVO loginUser = getLoginUser(session);
        if (loginUser == null) {
            return "redirect:/login.do?returnUrl=" + safeReturnUrl(returnUrl, "/lunch/today.do");
        }
        try {
            LunchVoteVO selected = lunchVoteService.vote(restaurantId, loginUser);
            redirectAttributes.addFlashAttribute("message", selected.getStoreName() + "에 투표했습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("message", e.getMessage());
        }
        return "redirect:" + safeReturnUrl(returnUrl, "/lunch/today.do");
    }

    @RequestMapping("/lunch/random.do")
    public String random(HttpSession session, RedirectAttributes redirectAttributes) {
        UserVO loginUser = getLoginUser(session);
        if (loginUser == null) {
            return "redirect:/login.do?returnUrl=/lunch/today.do";
        }
        try {
            LunchVoteVO selected = lunchVoteService.randomVote(loginUser);
            redirectAttributes.addFlashAttribute("message", "랜덤 선택 결과: " + selected.getStoreName() + "에 투표했습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("message", e.getMessage());
        }
        return "redirect:/lunch/today.do";
    }
    @RequestMapping("/lunch/cancel.do")
    public String cancel(@RequestParam(value = "returnUrl", required = false) String returnUrl,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        UserVO loginUser = getLoginUser(session);
        if (loginUser == null) {
            return "redirect:/login.do?returnUrl=" + safeReturnUrl(returnUrl, "/lunch/today.do");
        }
        try {
            lunchVoteService.cancelTodayVote(loginUser);
            redirectAttributes.addFlashAttribute("message", "오늘 점심 선택을 취소했습니다. 다시 투표할 수 있습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("message", e.getMessage());
        }
        return "redirect:" + safeReturnUrl(returnUrl, "/lunch/today.do");
    }

    private UserVO getLoginUser(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object loginUser = session.getAttribute(AuthController.LOGIN_SESSION_KEY);
        return loginUser instanceof UserVO ? (UserVO) loginUser : null;
    }

    private String safeReturnUrl(String returnUrl, String defaultUrl) {
        if (returnUrl != null && returnUrl.startsWith("/") && !returnUrl.startsWith("//") && !returnUrl.contains("://")) {
            return returnUrl;
        }
        return defaultUrl;
    }
}
package com.aonfine.comment.web;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.aonfine.auth.service.UserVO;
import com.aonfine.auth.web.AuthController;
import com.aonfine.comment.service.RestaurantCommentService;
import com.aonfine.comment.service.RestaurantCommentVO;

@Controller
public class RestaurantCommentController {

    @Resource(name = "restaurantCommentService")
    private RestaurantCommentService restaurantCommentService;

    @RequestMapping("/restaurant/comment/insert.do")
    public String insert(@ModelAttribute RestaurantCommentVO commentVO,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        UserVO loginUser = getLoginUser(session);
        if (loginUser == null) {
            return "redirect:/login.do?returnUrl=/restaurant/detail.do?restaurantId=" + commentVO.getRestaurantId();
        }
        commentVO.setUserId(loginUser.getUserId());
        commentVO.setUserName(loginUser.getUserName());
        try {
            restaurantCommentService.insertComment(commentVO);
            redirectAttributes.addFlashAttribute("message", "댓글이 등록되었습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("message", e.getMessage());
        }
        return "redirect:/restaurant/detail.do?restaurantId=" + commentVO.getRestaurantId();
    }

    @RequestMapping("/restaurant/comment/delete.do")
    public String delete(@ModelAttribute RestaurantCommentVO commentVO,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        UserVO loginUser = getLoginUser(session);
        if (loginUser == null) {
            return "redirect:/login.do?returnUrl=/restaurant/detail.do?restaurantId=" + commentVO.getRestaurantId();
        }
        restaurantCommentService.deleteComment(commentVO.getCommentId(), loginUser.getUserId());
        redirectAttributes.addFlashAttribute("message", "댓글이 삭제되었습니다.");
        return "redirect:/restaurant/detail.do?restaurantId=" + commentVO.getRestaurantId();
    }

    private UserVO getLoginUser(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object loginUser = session.getAttribute(AuthController.LOGIN_SESSION_KEY);
        return loginUser instanceof UserVO ? (UserVO) loginUser : null;
    }
}
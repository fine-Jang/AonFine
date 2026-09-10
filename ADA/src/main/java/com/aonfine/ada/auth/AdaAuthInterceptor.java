package com.aonfine.ada.auth;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 로그인 화면/정적 리소스를 제외한 모든 요청에 로그인을 요구한다.
 * 소유권(본인 내역 vs 관리자 전체) 검사는 별도로 각 서비스/컨트롤러에서 수행한다.
 */
public class AdaAuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        HttpSession session = request.getSession(false);
        Object user = session == null ? null : session.getAttribute(AdaSessionConstants.LOGIN_SESSION_KEY);
        if (user != null) {
            return true;
        }
        if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"message\":\"로그인이 필요합니다. 다시 로그인한 뒤 목록에서 상태를 확인해 주세요.\"}");
        } else {
            response.sendRedirect(request.getContextPath() + "/login.do");
        }
        return false;
    }
}

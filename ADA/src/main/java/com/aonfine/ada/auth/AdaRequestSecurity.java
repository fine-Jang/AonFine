package com.aonfine.ada.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import com.aonfine.ada.dump.DumpVO;

public final class AdaRequestSecurity {
    private static final String KEY = "adaCsrfToken";
    private AdaRequestSecurity() { }
    public static String token(HttpSession session) {
        synchronized (session) {
            String value = (String) session.getAttribute(KEY);
            if (value == null) { value = UUID.randomUUID().toString(); session.setAttribute(KEY, value); }
            return value;
        }
    }
    public static boolean validMutation(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        String expected = session == null ? null : (String) session.getAttribute(KEY);
        String actual = request.getHeader("X-ADA-CSRF");
        return "POST".equals(request.getMethod()) && expected != null && actual != null
                && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
    public static AdaUserVO user(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (AdaUserVO) session.getAttribute(AdaSessionConstants.LOGIN_SESSION_KEY);
    }
    public static boolean canAccess(AdaUserVO user, DumpVO vo) {
        return user != null && vo != null && (user.isAdmin() || user.getUserId().equals(vo.getUserId()));
    }
    public static boolean validId(String id) {
        return id != null && id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }
}

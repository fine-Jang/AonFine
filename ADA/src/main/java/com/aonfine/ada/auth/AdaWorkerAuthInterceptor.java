package com.aonfine.ada.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Auth for /worker/** only -- entirely separate from the user session (AdaAuthInterceptor).
 * Bearer token from ada.properties (never hardcoded, never logged) plus an optional source-IP
 * allowlist. No token configured = fail closed (every /worker/** request is rejected), so an
 * accidental redeploy without operator-set credentials cannot silently open the internal API.
 */
public class AdaWorkerAuthInterceptor implements HandlerInterceptor {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdaWorkerAuthInterceptor.class);

    @Value("${ada.worker.apiToken:}")
    private String apiToken;

    @Value("${ada.worker.allowedIps:}")
    private String allowedIpsCsv;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        response.setHeader("Cache-Control", "no-store");
        if (!StringUtils.hasText(apiToken)) {
            LOGGER.error("worker API call rejected: ada.worker.apiToken is not configured");
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return false;
        }
        Set<String> allowed = allowedIps();
        if (!allowed.isEmpty() && !allowed.contains(request.getRemoteAddr())) {
            LOGGER.warn("worker API call rejected: source {} not in allowlist", request.getRemoteAddr());
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }
        String header = request.getHeader("Authorization");
        String presented = header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
        if (presented == null || !constantTimeEquals(apiToken, presented)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        return true;
    }

    private Set<String> allowedIps() {
        Set<String> set = new HashSet<>();
        if (StringUtils.hasText(allowedIpsCsv)) {
            set.addAll(Arrays.asList(allowedIpsCsv.split(",")));
            set.removeIf(s -> !StringUtils.hasText(s));
        }
        return set;
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
}

package com.aonfine.ada.auth;

import static org.junit.Assert.*;
import static com.aonfine.ada.LocalDatabase.inject;

import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** /worker/** auth is a separate concern from the user session -- no login, no CSRF, token + optional IP allowlist only. */
public class AdaWorkerAuthInterceptorTest {
    private AdaWorkerAuthInterceptor interceptor;

    @Before public void setup() throws Exception {
        interceptor = new AdaWorkerAuthInterceptor();
    }

    @Test public void noTokenConfiguredFailsClosed() throws Exception {
        inject(interceptor, "apiToken", "");
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer whatever");
        MockHttpServletResponse res = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(req, res, null));
        assertEquals(503, res.getStatus());
    }

    @Test public void wrongTokenRejected() throws Exception {
        inject(interceptor, "apiToken", "secret-token");
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer wrong");
        MockHttpServletResponse res = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(req, res, null));
        assertEquals(401, res.getStatus());
    }

    @Test public void missingAuthorizationHeaderRejected() throws Exception {
        inject(interceptor, "apiToken", "secret-token");
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(req, res, null));
        assertEquals(401, res.getStatus());
    }

    @Test public void correctTokenAccepted() throws Exception {
        inject(interceptor, "apiToken", "secret-token");
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer secret-token");
        MockHttpServletResponse res = new MockHttpServletResponse();
        assertTrue(interceptor.preHandle(req, res, null));
    }

    @Test public void ipNotInAllowlistRejectedEvenWithCorrectToken() throws Exception {
        inject(interceptor, "apiToken", "secret-token");
        inject(interceptor, "allowedIpsCsv", "192.168.2.17");
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.9");
        req.addHeader("Authorization", "Bearer secret-token");
        MockHttpServletResponse res = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(req, res, null));
        assertEquals(403, res.getStatus());
    }

    @Test public void ipInAllowlistAccepted() throws Exception {
        inject(interceptor, "apiToken", "secret-token");
        inject(interceptor, "allowedIpsCsv", "192.168.2.17");
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("192.168.2.17");
        req.addHeader("Authorization", "Bearer secret-token");
        MockHttpServletResponse res = new MockHttpServletResponse();
        assertTrue(interceptor.preHandle(req, res, null));
    }
}

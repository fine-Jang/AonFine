package com.aonfine.ada.upload;

import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static com.aonfine.ada.LocalDatabase.inject;
import org.junit.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.aonfine.ada.auth.*;
import com.aonfine.ada.dump.DumpVO;

public class UploadAccessTest {
    private static final String ID = "00000000-0000-0000-0000-000000000001";
    private MockMvc mvc;
    private StubUploads uploads;
    @Before public void setup() throws Exception {
        uploads = new StubUploads();
        DumpUploadController controller = new DumpUploadController(); inject(controller, "service", uploads);
        mvc = MockMvcBuilders.standaloneSetup(controller).addInterceptors(new AdaAuthInterceptor()).build();
    }
    @After public void stop() { uploads.stop(); }
    private MockHttpSession session(String id, boolean admin) {
        MockHttpSession session = new MockHttpSession(); AdaUserVO user = new AdaUserVO();
        user.setUserId(id); user.setRoleCode(admin ? "ADMIN" : "USER");
        session.setAttribute(AdaSessionConstants.LOGIN_SESSION_KEY, user); AdaRequestSecurity.token(session); return session;
    }
    @Test public void anonymousJsonIs401NotLoginHtml() throws Exception {
        mvc.perform(get("/dump/uploadStatus.do").param("dumpId", ID).header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
    }
    @Test public void otherUsersCannotReadOrCancel() throws Exception {
        MockHttpSession other = session("bob", false);
        mvc.perform(get("/dump/uploadStatus.do").session(other).param("dumpId", ID)).andExpect(status().isNotFound());
        mvc.perform(post("/dump/uploadCancel.do").session(other).param("dumpId", ID)
                .header("X-ADA-CSRF", AdaRequestSecurity.token(other))).andExpect(status().isNotFound());
        assertEquals(0, uploads.cancels);
    }
    @Test public void mutationsRequirePostAndCsrf() throws Exception {
        MockHttpSession owner = session("alice", false);
        mvc.perform(get("/dump/uploadCancel.do").session(owner).param("dumpId", ID)).andExpect(status().isMethodNotAllowed());
        mvc.perform(post("/dump/uploadCancel.do").session(owner).param("dumpId", ID)).andExpect(status().isForbidden());
        mvc.perform(post("/dump/upload.do").session(owner).header("X-ADA-Dump-ID", ID)).andExpect(status().isForbidden());
        assertEquals(0, uploads.cancels); assertEquals(0, uploads.bodies);
    }
    @Test public void ownerAndAdminCanCancelButAdminCannotUploadForOwner() throws Exception {
        for (MockHttpSession s : new MockHttpSession[] {session("alice", false), session("admin", true)}) {
            mvc.perform(post("/dump/uploadCancel.do").session(s).param("dumpId", ID)
                    .header("X-ADA-CSRF", AdaRequestSecurity.token(s))).andExpect(status().isOk());
        }
        assertEquals(2, uploads.cancels);
        MockHttpSession admin = session("admin", true);
        mvc.perform(post("/dump/upload.do").session(admin).header("X-ADA-Dump-ID", ID)
                .header("X-ADA-CSRF", AdaRequestSecurity.token(admin))).andExpect(status().isForbidden());
        assertEquals(0, uploads.bodies);
    }
    @Test public void malformedIdsNeverReachStorageLookup() throws Exception {
        mvc.perform(get("/dump/uploadStatus.do").session(session("alice", false)).param("dumpId", "../../data"))
                .andExpect(status().isNotFound()); assertEquals(0, uploads.lookups);
    }
    private static class StubUploads extends DumpUploadService {
        int cancels, bodies, lookups;
        @Override public DumpVO find(String id) {
            lookups++; if (!ID.equals(id)) return null;
            DumpVO vo = new DumpVO(); vo.setDumpId(ID); vo.setUserId("alice"); vo.setStatusCd(DumpVO.STATUS_UPLOADING); return vo;
        }
        @Override public UploadResult cancel(String id) { cancels++; return UploadResult.state(find(id)); }
        @Override public UploadResult handleUpload(javax.servlet.http.HttpServletRequest request, String id) { bodies++; return UploadResult.state(find(id)); }
    }
}

package com.aonfine.ada.dump.web;

import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static com.aonfine.ada.LocalDatabase.inject;

import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.aonfine.ada.analysis.AnalysisResult;
import com.aonfine.ada.analysis.AnalysisService;
import com.aonfine.ada.auth.AdaAuthInterceptor;
import com.aonfine.ada.auth.AdaRequestSecurity;
import com.aonfine.ada.auth.AdaSessionConstants;
import com.aonfine.ada.auth.AdaUserVO;
import com.aonfine.ada.dump.DumpVO;

/** Controller-level auth/CSRF/ownership wiring for /dump/analyze*.do, independent of AnalysisServiceTest's real-DB CAS coverage. */
public class AnalyzeControllerTest {
    private static final String ID = "00000000-0000-0000-0000-000000000001";
    private MockMvc mvc;
    private StubAnalysisService service;

    private StubDumpService dumps;
    private java.io.File resultRoot;

    @Before public void setup() throws Exception {
        service = new StubAnalysisService();
        dumps = new StubDumpService();
        resultRoot = java.nio.file.Files.createTempDirectory("ada-results").toFile();
        com.aonfine.ada.common.AdaProperties props = new com.aonfine.ada.common.AdaProperties();
        props.setResultRoot(resultRoot.getAbsolutePath());
        props.setRetentionDays(30);
        AnalyzeController controller = new AnalyzeController();
        inject(controller, "analysisService", service);
        inject(controller, "dumpService", dumps);
        inject(controller, "adaProperties", props);
        mvc = MockMvcBuilders.standaloneSetup(controller).addInterceptors(new AdaAuthInterceptor()).build();
    }

    private MockHttpSession session(String id, boolean admin) {
        MockHttpSession session = new MockHttpSession();
        AdaUserVO user = new AdaUserVO();
        user.setUserId(id); user.setRoleCode(admin ? "ADMIN" : "USER");
        session.setAttribute(AdaSessionConstants.LOGIN_SESSION_KEY, user);
        AdaRequestSecurity.token(session);
        return session;
    }

    @Test public void statusRequiresLogin() throws Exception {
        mvc.perform(get("/dump/analysisStatus.do").param("dumpId", ID).header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
    }

    @Test public void requestRequiresPostAndCsrf() throws Exception {
        MockHttpSession owner = session("alice", false);
        mvc.perform(get("/dump/analyzeRequest.do").session(owner).param("dumpId", ID)).andExpect(status().isMethodNotAllowed());
        mvc.perform(post("/dump/analyzeRequest.do").session(owner).param("dumpId", ID)).andExpect(status().isForbidden());
        assertEquals(0, service.requestCalls);
    }

    @Test public void malformedDumpIdNeverReachesService() throws Exception {
        MockHttpSession owner = session("alice", false);
        mvc.perform(get("/dump/analysisStatus.do").session(owner).param("dumpId", "../etc/passwd"))
                .andExpect(status().isNotFound());
        assertEquals(0, service.statusCalls);
    }

    @Test public void validRequestReachesServiceAndReturnsItsResult() throws Exception {
        MockHttpSession owner = session("alice", false);
        service.nextRequestResult = AnalysisResult.of(ID, job("QUEUED"), null);
        String json = mvc.perform(post("/dump/analyzeRequest.do").session(owner).param("dumpId", ID)
                .header("X-ADA-CSRF", AdaRequestSecurity.token(owner))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(1, service.requestCalls);
        assertTrue(json.contains("QUEUED"));
    }

    @Test public void conflictResultMapsTo409() throws Exception {
        MockHttpSession owner = session("alice", false);
        service.nextCancelResult = AnalysisResult.fail("ALREADY_TERMINAL", "이미 종료된 분석입니다.");
        mvc.perform(post("/dump/analysisCancel.do").session(owner).param("dumpId", ID)
                .header("X-ADA-CSRF", AdaRequestSecurity.token(owner))).andExpect(status().isConflict());
    }

    private static com.aonfine.ada.analysis.AnalysisJobVO job(String status) {
        com.aonfine.ada.analysis.AnalysisJobVO job = new com.aonfine.ada.analysis.AnalysisJobVO();
        job.setJobId("job-1"); job.setDumpId(ID); job.setStatusCd(status); job.setVersionNo(1);
        return job;
    }

    // ---- result download ----

    private com.aonfine.ada.analysis.AnalysisAttemptVO succeededAttempt(String resultPath) {
        com.aonfine.ada.analysis.AnalysisAttemptVO a = new com.aonfine.ada.analysis.AnalysisAttemptVO();
        a.setAttemptId("attempt-1");
        a.setStatusCd("SUCCEEDED");
        a.setResultPath(resultPath);
        return a;
    }

    @Test public void downloadStreamsTheResultZipForTheOwner() throws Exception {
        java.io.File attemptDir = new java.io.File(resultRoot, "attempt-1");
        attemptDir.mkdirs();
        java.nio.file.Files.write(new java.io.File(attemptDir, "result.zip").toPath(), "PK-zip-bytes".getBytes());
        service.nextAttempt = succeededAttempt("attempt-1/result.zip");

        byte[] body = mvc.perform(get("/dump/analyzeResultDownload.do").session(session("alice", false)).param("dumpId", ID))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertEquals("PK-zip-bytes", new String(body));
    }

    @Test public void downloadIsRejectedWhileTheAnalysisIsNotSucceeded() throws Exception {
        com.aonfine.ada.analysis.AnalysisAttemptVO running = succeededAttempt("attempt-1/result.zip");
        running.setStatusCd("RUNNING");
        service.nextAttempt = running;
        mvc.perform(get("/dump/analyzeResultDownload.do").session(session("alice", false)).param("dumpId", ID))
                .andExpect(status().isConflict());
    }

    @Test public void downloadRefusesAResultPathEscapingTheResultRoot() throws Exception {
        java.io.File outside = java.nio.file.Files.createTempFile("outside", ".zip").toFile();
        java.nio.file.Files.write(outside.toPath(), "secret".getBytes());
        service.nextAttempt = succeededAttempt("../" + outside.getName());
        mvc.perform(get("/dump/analyzeResultDownload.do").session(session("alice", false)).param("dumpId", ID))
                .andExpect(status().isNotFound());
    }

    @Test public void downloadRequiresLogin() throws Exception {
        mvc.perform(get("/dump/analyzeResultDownload.do").param("dumpId", ID).header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
    }

    @Test public void downloadIsNotFoundWhenTheDumpIsNotAccessibleToThisUser() throws Exception {
        dumps.accessible = false;
        service.nextAttempt = succeededAttempt("attempt-1/result.zip");
        mvc.perform(get("/dump/analyzeResultDownload.do").session(session("bob", false)).param("dumpId", ID))
                .andExpect(status().isNotFound());
    }

    private static class StubDumpService extends com.aonfine.ada.dump.DumpService {
        boolean accessible = true;
        @Override public DumpVO getForAnalysis(String dumpId, boolean admin, String currentUserId) {
            if (!accessible) return null;
            DumpVO vo = new DumpVO();
            vo.setDumpId(ID); vo.setUserId("alice"); vo.setStatusCd(DumpVO.STATUS_STORED);
            vo.setDeptNm("통계청"); vo.setTaskNm("Aonfine");
            return vo;
        }
    }

    private static class StubAnalysisService extends AnalysisService {
        int requestCalls, statusCalls;
        AnalysisResult nextRequestResult = AnalysisResult.fail("NOT_FOUND", "not found");
        AnalysisResult nextCancelResult = AnalysisResult.fail("NOT_FOUND", "not found");
        com.aonfine.ada.analysis.AnalysisAttemptVO nextAttempt;
        @Override public AnalysisResult requestAttempt(String dumpId, String userId, String requestKey) {
            requestCalls++; return nextRequestResult;
        }
        @Override public AnalysisResult status(String dumpId, boolean admin, String userId) {
            statusCalls++; return AnalysisResult.idle(dumpId);
        }
        @Override public AnalysisResult cancel(String dumpId, boolean admin, String userId) {
            return nextCancelResult;
        }
        @Override public com.aonfine.ada.analysis.AnalysisAttemptVO currentAttemptFor(String dumpId, boolean admin, String userId) {
            return nextAttempt;
        }
    }
}

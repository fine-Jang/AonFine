package com.aonfine.ada.analysis;

import static org.junit.Assert.*;
import static com.aonfine.ada.LocalDatabase.inject;

import java.sql.Connection;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.Before;
import org.junit.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import com.aonfine.ada.LocalDatabase;
import com.aonfine.ada.analysis.impl.AnalysisJdbcMapper;
import com.aonfine.ada.dump.DumpMapper;
import com.aonfine.ada.dump.DumpService;
import com.aonfine.ada.dump.DumpVO;
import com.aonfine.ada.dump.impl.DumpJdbcMapper;
import com.aonfine.ada.common.AdaProperties;

/**
 * Exercises the real JOB/ATTEMPT CAS transactions end to end against H2 (translated CUBRID DDL),
 * with a genuine Spring transactional proxy around AnalysisService so @Transactional rollback
 * behaves as it will under tx:annotation-driven in the deployed app -- not a plain POJO call.
 * NOT a CUBRID certification (see LocalDatabase javadoc / MIGRATION.md).
 */
public class AnalysisServiceTest {
    private static final String DUMP_ID = "dump-1";
    private DataSource ds;
    private AnalysisMapper analysisMapper;
    private DumpMapper dumpMapper;
    private AnalysisService service;

    @Before public void setup() throws Exception {
        ds = LocalDatabase.create();
        LocalDatabase.run(ds, "sql/ada-analysis-tables-cubrid.sql");

        analysisMapper = new AnalysisJdbcMapper();
        inject(analysisMapper, "jdbcTemplate", new org.springframework.jdbc.core.JdbcTemplate(ds));
        DumpJdbcMapper dumpJdbcMapper = new DumpJdbcMapper();
        inject(dumpJdbcMapper, "jdbcTemplate", new org.springframework.jdbc.core.JdbcTemplate(ds));
        dumpMapper = dumpJdbcMapper;

        AdaProperties props = new AdaProperties();
        props.setRetentionDays(30);
        DumpService dumpService = new DumpService();
        inject(dumpService, "dumpMapper", dumpMapper);
        inject(dumpService, "adaProperties", props);

        AnalysisService target = new AnalysisService();
        inject(target, "analysisMapper", analysisMapper);
        inject(target, "dumpMapper", dumpMapper);
        inject(target, "dumpService", dumpService);

        DataSourceTransactionManager txManager = new DataSourceTransactionManager(ds);
        TransactionInterceptor txAdvice = new TransactionInterceptor(txManager, new AnnotationTransactionAttributeSource());
        ProxyFactory pf = new ProxyFactory(target);
        pf.addAdvice(txAdvice);
        service = (AnalysisService) pf.getProxy();

        insertStoredDump(DUMP_ID, "alice");
    }

    private void insertStoredDump(String dumpId, String userId) throws Exception {
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute("INSERT INTO TB_ADA_DUMP(DUMP_ID,USER_ID,DUMP_TYPE,DEPT_NM,TASK_NM,HOST_NM,ORIGINAL_FILE_NM,STORED_FILE_NM,FILE_SIZE,STATUS_CD) "
                    + "VALUES('" + dumpId + "','" + userId + "','HEAP','dept','task','host','f.tar.gz','" + dumpId + ".tar.gz',12,'STORED')");
        }
    }

    @Test public void firstRequestCreatesJobAndQueuedAttempt() {
        AnalysisResult r = service.requestAttempt(DUMP_ID, "alice", null);
        assertTrue(r.isSuccess());
        assertEquals("QUEUED", r.getStatusCd());
        assertEquals(1, r.getAttemptNo());
        assertNotNull(r.getJobId());
        assertNotNull(r.getAttemptId());
    }

    @Test public void duplicateRequestWhileQueuedDoesNotCreateSecondAttempt() {
        AnalysisResult first = service.requestAttempt(DUMP_ID, "alice", null);
        AnalysisResult second = service.requestAttempt(DUMP_ID, "alice", null);
        assertEquals(first.getAttemptId(), second.getAttemptId());
        assertEquals(1, analysisMapper.selectMaxAttemptNo(first.getJobId()));
    }

    @Test public void otherUsersCannotRequestOrSeeSomeoneElsesDump() {
        AnalysisResult r = service.requestAttempt(DUMP_ID, "bob", null);
        assertFalse(r.isSuccess());
        assertEquals("NOT_FOUND", r.getCode());
    }

    @Test public void nonStoredDumpIsRejected() throws Exception {
        insertStoredDump("dump-uploading", "alice");
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute("UPDATE TB_ADA_DUMP SET STATUS_CD='UPLOADING' WHERE DUMP_ID='dump-uploading'");
        }
        AnalysisResult r = service.requestAttempt("dump-uploading", "alice", null);
        assertFalse(r.isSuccess());
        assertEquals("NOT_READY", r.getCode());
    }

    @Test public void claimTransitionsToRunningAndBlocksNewAttempts() {
        AnalysisResult requested = service.requestAttempt(DUMP_ID, "alice", null);
        AnalysisAttemptVO claimed = service.claimNext("worker-1");
        assertNotNull(claimed);
        assertEquals(requested.getAttemptId(), claimed.getAttemptId());
        assertEquals("RUNNING", claimed.getStatusCd());
        assertEquals("RUNNING", service.getJob(requested.getJobId()).getStatusCd());

        AnalysisResult secondRequest = service.requestAttempt(DUMP_ID, "alice", null);
        assertEquals(requested.getAttemptId(), secondRequest.getAttemptId());
        assertEquals(1, analysisMapper.selectMaxAttemptNo(requested.getJobId()));
    }

    @Test public void claimReturnsNullWhenNothingQueued() {
        assertNull(service.claimNext("worker-1"));
    }

    @Test public void cancelQueuedGoesStraightToCancelledWithoutAWorker() {
        AnalysisResult requested = service.requestAttempt(DUMP_ID, "alice", null);
        AnalysisResult cancelled = service.cancel(DUMP_ID, false, "alice");
        assertTrue(cancelled.isSuccess());
        assertEquals("CANCELLED", cancelled.getStatusCd());
        assertTrue(cancelled.isRetryAllowed());
    }

    @Test public void cancelRunningOnlyRequestsUntilWorkerConfirms() {
        service.requestAttempt(DUMP_ID, "alice", null);
        service.claimNext("worker-1");
        AnalysisResult cancelled = service.cancel(DUMP_ID, false, "alice");
        assertTrue(cancelled.isSuccess());
        assertEquals("CANCEL_REQUESTED", cancelled.getStatusCd());
        assertFalse(cancelled.isTerminal());
    }

    @Test public void adminCanCancelButNotRequestForAnotherUser() {
        service.requestAttempt(DUMP_ID, "alice", null);
        AnalysisResult adminRequest = service.requestAttempt(DUMP_ID, "admin", null);
        assertFalse(adminRequest.isSuccess()); // admin is not the owner, request is rejected as NOT_FOUND
        AnalysisResult adminCancel = service.cancel(DUMP_ID, true, "admin");
        assertTrue(adminCancel.isSuccess());
    }

    @Test public void workerReportSucceedsAndUpdatesJobAndDumpMirror() {
        AnalysisResult requested = service.requestAttempt(DUMP_ID, "alice", null);
        AnalysisAttemptVO claimed = service.claimNext("worker-1");
        long jobVersion = service.getJob(requested.getJobId()).getVersionNo();
        AnalysisResult reported = service.report(requested.getJobId(), claimed.getAttemptId(), "worker-1",
                jobVersion, claimed.getVersionNo(),
                "RUNNING", "SUCCEEDED", null, null, "reports/report.md");
        assertTrue("report should be accepted: " + reported.getMessage(), reported.isSuccess());
        assertEquals("SUCCEEDED", reported.getStatusCd());
        assertTrue(reported.isResultAvailable());
        DumpVO dump = dumpMapper.selectOne(DUMP_ID, new java.sql.Timestamp(0));
        assertEquals("SUCCEEDED", dump.getAnalysisStatusCd());
    }

    @Test public void staleReportIsRejectedAndDoesNotOverwrite() {
        AnalysisResult requested = service.requestAttempt(DUMP_ID, "alice", null);
        AnalysisAttemptVO claimed = service.claimNext("worker-1");
        long jobVersion = service.getJob(requested.getJobId()).getVersionNo();
        AnalysisResult stale = service.report(requested.getJobId(), claimed.getAttemptId(), "worker-1",
                jobVersion, claimed.getVersionNo() + 99 /* wrong expected version */,
                "RUNNING", "SUCCEEDED", null, null, "reports/report.md");
        assertFalse(stale.isSuccess());
        assertEquals("STALE", stale.getCode());
        AnalysisAttemptVO stillRunning = analysisMapper.selectAttemptById(claimed.getAttemptId());
        assertEquals("RUNNING", stillRunning.getStatusCd());
    }

    @Test public void resendingTheSameCompletedReportIsIdempotent() {
        AnalysisResult requested = service.requestAttempt(DUMP_ID, "alice", null);
        AnalysisAttemptVO claimed = service.claimNext("worker-1");
        long jobVersion = service.getJob(requested.getJobId()).getVersionNo();
        AnalysisResult first = service.report(requested.getJobId(), claimed.getAttemptId(), "worker-1",
                jobVersion, claimed.getVersionNo(), "RUNNING", "SUCCEEDED", null, null, "reports/report.md");
        AnalysisResult resend = service.report(requested.getJobId(), claimed.getAttemptId(), "worker-1",
                jobVersion, claimed.getVersionNo(), "RUNNING", "SUCCEEDED", null, null, "reports/report.md");
        assertTrue(first.isSuccess());
        assertTrue(resend.isSuccess());
        assertEquals(first.getAttemptId(), resend.getAttemptId());
    }

    @Test public void retryAfterFailureCreatesNewAttemptNumber() {
        AnalysisResult requested = service.requestAttempt(DUMP_ID, "alice", null);
        AnalysisAttemptVO claimed = service.claimNext("worker-1");
        long jobVersion = service.getJob(requested.getJobId()).getVersionNo();
        service.report(requested.getJobId(), claimed.getAttemptId(), "worker-1", jobVersion, claimed.getVersionNo(),
                "RUNNING", "FAILED", "MAT_ERROR", "heap dump parse failed", null);
        AnalysisResult retried = service.requestAttempt(DUMP_ID, "alice", null);
        assertTrue(retried.isSuccess());
        assertEquals(2, retried.getAttemptNo());
        assertNotEquals(claimed.getAttemptId(), retried.getAttemptId());
    }

    @Test public void heartbeatSilenceMovesToRecoveryRequiredNotToFailedOrCancelled() throws Exception {
        AnalysisResult requested = service.requestAttempt(DUMP_ID, "alice", null);
        AnalysisAttemptVO claimed = service.claimNext("worker-1");
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute("UPDATE TB_ADA_ANALYSIS_ATTEMPT SET LAST_HEARTBEAT_DT = TIMESTAMP '2000-01-01 00:00:00' WHERE ATTEMPT_ID = '"
                    + claimed.getAttemptId() + "'");
        }
        int recovered = service.recoverStaleHeartbeats(60, 10);
        assertEquals(1, recovered);
        AnalysisAttemptVO attempt = analysisMapper.selectAttemptById(claimed.getAttemptId());
        assertEquals("RECOVERY_REQUIRED", attempt.getStatusCd());
        assertEquals("RECOVERY_REQUIRED", service.getJob(requested.getJobId()).getStatusCd());
        // Cancel must not silently resolve an unconfirmed recovery: no plain-user path exists for it here.
        AnalysisResult retryBlocked = service.requestAttempt(DUMP_ID, "alice", null);
        assertEquals(attempt.getAttemptId(), retryBlocked.getAttemptId()); // still the same unresolved attempt, no new one made
    }

    @Test public void heartbeatOnlyAcceptedFromTheClaimingWorker() {
        service.requestAttempt(DUMP_ID, "alice", null);
        AnalysisAttemptVO claimed = service.claimNext("worker-1");
        assertTrue(service.heartbeat(claimed.getAttemptId(), "worker-1"));
        assertFalse(service.heartbeat(claimed.getAttemptId(), "worker-2"));
    }

    @Test public void concurrentFirstRequestsNeverProduceTwoQueuedAttemptsForTheSameDump() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        java.util.List<Future<AnalysisResult>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    return service.requestAttempt(DUMP_ID, "alice", null);
                } catch (RuntimeException raced) {
                    return null; // a losing thread may see a concurrency exception instead of a graceful result; that is acceptable, never silent corruption
                }
            }));
        }
        start.countDown();
        int successCount = 0;
        for (Future<AnalysisResult> f : futures) {
            AnalysisResult r = f.get(10, TimeUnit.SECONDS);
            if (r != null && r.isSuccess() && "QUEUED".equals(r.getStatusCd())) successCount++;
        }
        pool.shutdown();
        assertTrue(successCount >= 1);
        AnalysisJobVO job = analysisMapper.selectJobByDumpId(DUMP_ID);
        assertNotNull(job);
        assertEquals(1, analysisMapper.selectMaxAttemptNo(job.getJobId()));
    }
}

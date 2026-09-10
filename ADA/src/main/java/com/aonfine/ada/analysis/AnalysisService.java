package com.aonfine.ada.analysis;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.aonfine.ada.dump.DumpMapper;
import com.aonfine.ada.dump.DumpService;
import com.aonfine.ada.dump.DumpVO;

/**
 * Real JOB/ATTEMPT transactions behind Analyze/cancel/retry and the internal Worker API.
 * Every state change is a fixed-order JOB -> ATTEMPT -> DUMP conditional write inside one
 * @Transactional method; any 0-row CAS result throws to roll back the whole method (see
 * API-CONTRACT.md "향후 구현할 트랜잭션·보고 규칙"). This class is the first real implementation
 * of that contract -- AnalysisState stays a pure enum of allowed transitions.
 */
@Service("analysisService")
public class AnalysisService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnalysisService.class);

    @Resource(name = "analysisMapper") private AnalysisMapper analysisMapper;
    @Resource(name = "dumpMapper") private DumpMapper dumpMapper;
    @Resource(name = "dumpService") private DumpService dumpService;

    @Transactional
    public AnalysisResult requestAttempt(String dumpId, String userId, String clientRequestKey) {
        DumpVO dump = dumpService.getForAnalysis(dumpId, false, userId);
        if (dump == null) return AnalysisResult.fail("NOT_FOUND", "덤프를 찾을 수 없습니다.");
        if (!DumpVO.STATUS_STORED.equals(dump.getStatusCd()))
            return AnalysisResult.fail("NOT_READY", "업로드가 완료된 덤프만 분석할 수 있습니다.");
        if (dump.isExpiredNow())
            return AnalysisResult.fail("EXPIRED", "보관 기간이 지난 덤프는 분석할 수 없습니다.");

        AnalysisJobVO job = analysisMapper.selectJobByDumpId(dumpId);
        if (job == null) {
            job = createJob(dumpId, userId);
        }

        if (StringUtils.hasText(clientRequestKey)) {
            AnalysisAttemptVO existing = analysisMapper.selectAttemptByRequestKey(clientRequestKey);
            if (existing != null && existing.getJobId().equals(job.getJobId())) {
                return AnalysisResult.of(dumpId, job, existing);
            }
        }

        if (!job.state().mayCreateAttempt()) {
            AnalysisAttemptVO current = job.getCurrentAttemptId() == null ? null
                    : analysisMapper.selectAttempt(job.getJobId(), job.getCurrentAttemptId());
            return AnalysisResult.of(dumpId, job, current);
        }

        String attemptId = UUID.randomUUID().toString();
        String requestKey = StringUtils.hasText(clientRequestKey) ? clientRequestKey : UUID.randomUUID().toString();
        int nextNo = analysisMapper.selectMaxAttemptNo(job.getJobId()) + 1;

        AnalysisAttemptVO attempt = new AnalysisAttemptVO();
        attempt.setJobId(job.getJobId());
        attempt.setAttemptId(attemptId);
        attempt.setAttemptNo(nextNo);
        attempt.setRequestKey(requestKey);
        attempt.setRequestedBy(userId);
        try {
            analysisMapper.insertAttempt(attempt);
        } catch (DataIntegrityViolationException dup) {
            // REQUEST_KEY or (JOB_ID, ATTEMPT_NO) collided with a concurrent request; let the caller retry.
            throw new AnalysisConcurrencyException("attempt insert collided: " + dup.getMostSpecificCause());
        }
        int jobRows = analysisMapper.registerNewAttempt(job.getJobId(), job.getStatusCd(), job.getVersionNo(), attemptId, nextNo);
        if (jobRows != 1) throw new AnalysisConcurrencyException("job pointer changed concurrently for jobId=" + job.getJobId());
        dumpMapper.updateAnalysisStatus(dumpId, AnalysisState.QUEUED.name());

        AnalysisJobVO refreshedJob = analysisMapper.selectJobById(job.getJobId());
        AnalysisAttemptVO refreshedAttempt = analysisMapper.selectAttempt(job.getJobId(), attemptId);
        return AnalysisResult.of(dumpId, refreshedJob, refreshedAttempt);
    }

    private AnalysisJobVO createJob(String dumpId, String userId) {
        AnalysisJobVO job = new AnalysisJobVO();
        job.setJobId(UUID.randomUUID().toString());
        job.setDumpId(dumpId);
        job.setRequestedBy(userId);
        try {
            analysisMapper.insertJob(job);
        } catch (DataIntegrityViolationException dup) {
            // DUMP_ID is UNIQUE: another concurrent first-request already created it, use that row.
            AnalysisJobVO existing = analysisMapper.selectJobByDumpId(dumpId);
            if (existing == null) throw new AnalysisConcurrencyException("job insert collided but no row found for dumpId=" + dumpId);
            return existing;
        }
        return analysisMapper.selectJobByDumpId(dumpId);
    }

    @Transactional(readOnly = true)
    public AnalysisJobVO getJob(String jobId) {
        return analysisMapper.selectJobById(jobId);
    }

    /**
     * Current attempt of this dump's job, but only for a caller allowed to see this dump
     * (owner, or admin). Returns null when the dump isn't accessible or has no attempt yet.
     * Used by the result download, which still has to check the attempt's own state itself.
     */
    @Transactional(readOnly = true)
    public AnalysisAttemptVO currentAttemptFor(String dumpId, boolean admin, String userId) {
        DumpVO dump = dumpService.getForAnalysis(dumpId, admin, userId);
        if (dump == null) return null;
        AnalysisJobVO job = analysisMapper.selectJobByDumpId(dumpId);
        if (job == null || job.getCurrentAttemptId() == null) return null;
        return analysisMapper.selectAttempt(job.getJobId(), job.getCurrentAttemptId());
    }

    @Transactional(readOnly = true)
    public AnalysisResult status(String dumpId, boolean admin, String userId) {
        DumpVO dump = dumpService.getForAnalysis(dumpId, admin, userId);
        if (dump == null) return AnalysisResult.fail("NOT_FOUND", "덤프를 찾을 수 없습니다.");
        AnalysisJobVO job = analysisMapper.selectJobByDumpId(dumpId);
        if (job == null) return AnalysisResult.idle(dumpId);
        AnalysisAttemptVO attempt = job.getCurrentAttemptId() == null ? null
                : analysisMapper.selectAttempt(job.getJobId(), job.getCurrentAttemptId());
        return AnalysisResult.of(dumpId, job, attempt);
    }

    @Transactional
    public AnalysisResult cancel(String dumpId, boolean admin, String userId) {
        DumpVO dump = dumpService.getForAnalysis(dumpId, admin, userId);
        if (dump == null) return AnalysisResult.fail("NOT_FOUND", "덤프를 찾을 수 없습니다.");
        AnalysisJobVO job = analysisMapper.selectJobByDumpId(dumpId);
        if (job == null || job.getCurrentAttemptId() == null)
            return AnalysisResult.fail("NO_ACTIVE_ANALYSIS", "취소할 분석이 없습니다.");
        AnalysisAttemptVO attempt = analysisMapper.selectAttempt(job.getJobId(), job.getCurrentAttemptId());
        if (attempt == null) return AnalysisResult.fail("NOT_FOUND", "분석 시도를 찾을 수 없습니다.");

        AnalysisState state = attempt.state();
        if (state == AnalysisState.QUEUED) {
            int rows = analysisMapper.cancelQueuedAttempt(attempt.getAttemptId(), attempt.getVersionNo(), userId);
            if (rows != 1) return AnalysisResult.fail("CONFLICT", "요청 처리 중 상태가 변경되었습니다. 다시 시도해 주세요.");
            int jobRows = analysisMapper.updateJobStatus(job.getJobId(), AnalysisState.CANCELLED.name(),
                    attempt.getAttemptId(), job.getStatusCd(), job.getVersionNo());
            if (jobRows != 1) throw new AnalysisConcurrencyException("job pointer changed concurrently for jobId=" + job.getJobId());
            dumpMapper.updateAnalysisStatus(dumpId, AnalysisState.CANCELLED.name());
        } else if (state == AnalysisState.RUNNING) {
            int rows = analysisMapper.markCancelRequested(attempt.getAttemptId(), AnalysisState.RUNNING.name(),
                    attempt.getVersionNo(), userId);
            if (rows != 1) return AnalysisResult.fail("CONFLICT", "요청 처리 중 상태가 변경되었습니다. 다시 시도해 주세요.");
            int jobRows = analysisMapper.updateJobStatus(job.getJobId(), AnalysisState.CANCEL_REQUESTED.name(),
                    attempt.getAttemptId(), job.getStatusCd(), job.getVersionNo());
            if (jobRows != 1) throw new AnalysisConcurrencyException("job pointer changed concurrently for jobId=" + job.getJobId());
            dumpMapper.updateAnalysisStatus(dumpId, AnalysisState.CANCEL_REQUESTED.name());
        } else if (state == AnalysisState.CANCEL_REQUESTED) {
            // Already requested; not an error, just report current state.
        } else {
            return AnalysisResult.fail("ALREADY_TERMINAL", "이미 종료된 분석입니다. 재시도 버튼으로 새로 요청해 주세요.");
        }

        AnalysisJobVO refreshedJob = analysisMapper.selectJobById(job.getJobId());
        AnalysisAttemptVO refreshedAttempt = analysisMapper.selectAttempt(job.getJobId(), attempt.getAttemptId());
        return AnalysisResult.of(dumpId, refreshedJob, refreshedAttempt);
    }

    // ---- Internal Worker API (separate authentication, see AdaWorkerAuthInterceptor) ----

    @Transactional
    public AnalysisAttemptVO claimNext(String workerId) {
        List<AnalysisAttemptVO> candidates = analysisMapper.selectOldestQueued(20);
        for (AnalysisAttemptVO candidate : candidates) {
            int rows = analysisMapper.claimAttempt(candidate.getAttemptId(), workerId, candidate.getVersionNo());
            if (rows != 1) continue; // someone else claimed or cancelled it first; try the next candidate
            AnalysisJobVO job = analysisMapper.selectJobById(candidate.getJobId());
            int jobRows = analysisMapper.updateJobStatus(job.getJobId(), AnalysisState.RUNNING.name(),
                    candidate.getAttemptId(), job.getStatusCd(), job.getVersionNo());
            if (jobRows != 1) throw new AnalysisConcurrencyException("job pointer changed concurrently while claiming jobId=" + job.getJobId());
            dumpMapper.updateAnalysisStatus(job.getDumpId(), AnalysisState.RUNNING.name());
            return analysisMapper.selectAttempt(candidate.getJobId(), candidate.getAttemptId());
        }
        return null;
    }

    /** @return true if recorded; false means the attempt is no longer this worker's RUNNING/CANCEL_REQUESTED row -- worker must reconcile via status, not assume it may keep running. */
    @Transactional
    public boolean heartbeat(String attemptId, String workerId) {
        return analysisMapper.updateHeartbeat(attemptId, workerId) == 1;
    }

    @Transactional
    public AnalysisResult report(String jobId, String attemptId, String workerId, long expectedJobVersion,
            long expectedAttemptVersion, String expectedState, String newState, String failCode, String failReason, String resultPath) {
        AnalysisAttemptVO attempt = analysisMapper.selectAttemptById(attemptId);
        if (attempt == null || !attempt.getJobId().equals(jobId)) return AnalysisResult.fail("NOT_FOUND", "시도를 찾을 수 없습니다.");
        AnalysisJobVO job = analysisMapper.selectJobById(jobId);
        if (job == null) return AnalysisResult.fail("NOT_FOUND", "작업을 찾을 수 없습니다.");

        // Idempotent resend of the exact same completed report: return what is already stored, do not re-apply.
        if (attempt.getStatusCd().equals(newState) && isTerminal(newState) && workerId.equals(attempt.getWorkerId())) {
            return AnalysisResult.of(job.getDumpId(), job, attempt);
        }

        AnalysisState current = safeState(attempt.getStatusCd());
        AnalysisState next = safeState(newState);
        boolean accepted = current != null && next != null
                && AnalysisState.acceptsReport(job.getCurrentAttemptId(), attemptId, attempt.getVersionNo(),
                        expectedAttemptVersion, current, next)
                && attempt.getStatusCd().equals(expectedState)
                && workerId.equals(attempt.getWorkerId());
        if (!accepted) {
            LOGGER.info("stale/late analysis report rejected: jobId={} attemptId={} reportedFrom={} reportedTo={}",
                    jobId, attemptId, expectedState, newState);
            return AnalysisResult.fail("STALE", "오래되었거나 예상 상태와 다른 보고입니다. 무시합니다.");
        }

        int rows = analysisMapper.finalizeAttempt(attemptId, workerId, expectedAttemptVersion, expectedState, newState,
                failCode, failReason, resultPath);
        if (rows != 1) return AnalysisResult.fail("STALE", "오래되었거나 예상 상태와 다른 보고입니다. 무시합니다.");
        int jobRows = analysisMapper.updateJobStatus(jobId, newState, attemptId, job.getStatusCd(), expectedJobVersion);
        if (jobRows != 1) throw new AnalysisConcurrencyException("job pointer changed concurrently for jobId=" + jobId);
        dumpMapper.updateAnalysisStatus(job.getDumpId(), newState);

        AnalysisJobVO refreshedJob = analysisMapper.selectJobById(jobId);
        AnalysisAttemptVO refreshedAttempt = analysisMapper.selectAttemptById(attemptId);
        return AnalysisResult.of(job.getDumpId(), refreshedJob, refreshedAttempt);
    }

    /** Heartbeat silence alone is never proof the process stopped -- moves to RECOVERY_REQUIRED, not FAILED/CANCELLED. */
    @Transactional
    public int recoverStaleHeartbeats(int heartbeatTimeoutSeconds, int limit) {
        Timestamp cutoff = Timestamp.valueOf(LocalDateTime.now().minusSeconds(Math.max(30, heartbeatTimeoutSeconds)));
        List<AnalysisAttemptVO> stale = analysisMapper.selectStaleActive(cutoff, limit);
        int recovered = 0;
        for (AnalysisAttemptVO attempt : stale) {
            int rows = analysisMapper.markRecoveryRequired(attempt.getAttemptId(), attempt.getStatusCd(), attempt.getVersionNo());
            if (rows != 1) continue; // changed concurrently (e.g. a late report just landed); leave it, next cycle re-evaluates
            AnalysisJobVO job = analysisMapper.selectJobById(attempt.getJobId());
            if (job != null && attempt.getAttemptId().equals(job.getCurrentAttemptId())) {
                int jobRows = analysisMapper.updateJobStatus(job.getJobId(), AnalysisState.RECOVERY_REQUIRED.name(),
                        attempt.getAttemptId(), job.getStatusCd(), job.getVersionNo());
                if (jobRows == 1) {
                    dumpMapper.updateAnalysisStatus(job.getDumpId(), AnalysisState.RECOVERY_REQUIRED.name());
                }
            }
            recovered++;
            LOGGER.warn("analysis attempt marked RECOVERY_REQUIRED after heartbeat silence: jobId={} attemptId={}",
                    attempt.getJobId(), attempt.getAttemptId());
        }
        return recovered;
    }

    private static boolean isTerminal(String status) {
        return AnalysisState.CANCELLED.name().equals(status) || AnalysisState.FAILED.name().equals(status)
                || AnalysisState.SUCCEEDED.name().equals(status);
    }

    private static AnalysisState safeState(String value) {
        try { return AnalysisState.valueOf(value); } catch (Exception e) { return null; }
    }
}

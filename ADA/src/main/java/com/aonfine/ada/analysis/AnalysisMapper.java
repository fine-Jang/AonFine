package com.aonfine.ada.analysis;

import java.sql.Timestamp;
import java.util.List;

/**
 * TB_ADA_ANALYSIS_JOB / TB_ADA_ANALYSIS_ATTEMPT access. Every state-changing method is a single
 * conditional UPDATE (WHERE ... AND STATUS_CD = ? AND VERSION_NO = ?) returning the affected row
 * count; callers must treat 0 as "someone else changed it first", never as success.
 */
public interface AnalysisMapper {

    void insertJob(AnalysisJobVO vo);

    AnalysisJobVO selectJobByDumpId(String dumpId);

    AnalysisJobVO selectJobById(String jobId);

    void insertAttempt(AnalysisAttemptVO vo);

    int selectMaxAttemptNo(String jobId);

    AnalysisAttemptVO selectAttempt(String jobId, String attemptId);

    AnalysisAttemptVO selectAttemptById(String attemptId);

    AnalysisAttemptVO selectAttemptByRequestKey(String requestKey);

    /** New QUEUED attempt becomes the job's current pointer. 0 rows = job state/version changed concurrently. */
    int registerNewAttempt(String jobId, String expectedStatus, long expectedVersion, String newAttemptId, int newAttemptNo);

    int updateJobStatus(String jobId, String newStatus, String expectedCurrentAttemptId, String expectedStatus, long expectedVersion);

    /** QUEUED -> RUNNING, only if still unclaimed. */
    int claimAttempt(String attemptId, String workerId, long expectedVersion);

    int updateHeartbeat(String attemptId, String workerId);

    /** QUEUED (unclaimed) -> CANCELLED directly; no worker was ever running it. */
    int cancelQueuedAttempt(String attemptId, long expectedVersion, String cancelledBy);

    /** RUNNING -> CANCEL_REQUESTED; actual CANCELLED must come from the worker's report once it confirms the process stopped. */
    int markCancelRequested(String attemptId, String expectedStatus, long expectedVersion, String requestedBy);

    /** Worker's terminal report (SUCCEEDED/FAILED/CANCELLED), fenced by worker id + expected status/version. */
    int finalizeAttempt(String attemptId, String workerId, long expectedVersion, String expectedStatus, String newStatus,
            String failCode, String failReason, String resultPath);

    int markRecoveryRequired(String attemptId, String expectedStatus, long expectedVersion);

    List<AnalysisAttemptVO> selectOldestQueued(int limit);

    List<AnalysisAttemptVO> selectStaleActive(Timestamp heartbeatCutoff, int limit);
}

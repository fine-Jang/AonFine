package com.aonfine.ada.worker;

import com.aonfine.ada.analysis.AnalysisAttemptVO;
import com.aonfine.ada.dump.DumpVO;

/** What the Worker needs to locate the stored dump and process one claimed attempt. No credentials in here. */
public class WorkerClaimResult {
    private final boolean success;
    private final boolean available;
    private final String message;
    private final String jobId, attemptId, workerId, dumpId, dumpType, deptNm, taskNm, hostNm, originalFileNm, storedFileNm;
    private final int attemptNo;
    private final long attemptVersionNo, jobVersionNo;

    private WorkerClaimResult(boolean success, boolean available, String message, String jobId, String attemptId,
            String workerId, int attemptNo, long attemptVersionNo, long jobVersionNo, DumpVO dump) {
        this.success = success; this.available = available; this.message = message;
        this.jobId = jobId; this.attemptId = attemptId; this.workerId = workerId;
        this.attemptNo = attemptNo; this.attemptVersionNo = attemptVersionNo; this.jobVersionNo = jobVersionNo;
        this.dumpId = dump == null ? null : dump.getDumpId();
        this.dumpType = dump == null ? null : dump.getDumpType();
        this.deptNm = dump == null ? null : dump.getDeptNm();
        this.taskNm = dump == null ? null : dump.getTaskNm();
        this.hostNm = dump == null ? null : dump.getHostNm();
        this.originalFileNm = dump == null ? null : dump.getOriginalFileNm();
        this.storedFileNm = dump == null ? null : dump.getStoredFileNm();
    }

    public static WorkerClaimResult none() {
        return new WorkerClaimResult(true, false, "대기 중인 분석이 없습니다.", null, null, null, 0, 0, 0, null);
    }

    public static WorkerClaimResult claimed(AnalysisAttemptVO attempt, String workerId, long jobVersionNo, DumpVO dump) {
        return new WorkerClaimResult(true, true, "분석 시도를 배정했습니다.", attempt.getJobId(), attempt.getAttemptId(),
                workerId, attempt.getAttemptNo(), attempt.getVersionNo(), jobVersionNo, dump);
    }

    public static WorkerClaimResult fail(String message) {
        return new WorkerClaimResult(false, false, message, null, null, null, 0, 0, 0, null);
    }

    public boolean isSuccess() { return success; }
    public boolean isAvailable() { return available; }
    public String getMessage() { return message; }
    public String getJobId() { return jobId; }
    public String getAttemptId() { return attemptId; }
    public String getWorkerId() { return workerId; }
    public int getAttemptNo() { return attemptNo; }
    public long getAttemptVersionNo() { return attemptVersionNo; }
    public long getJobVersionNo() { return jobVersionNo; }
    public String getDumpId() { return dumpId; }
    public String getDumpType() { return dumpType; }
    public String getDeptNm() { return deptNm; }
    public String getTaskNm() { return taskNm; }
    public String getHostNm() { return hostNm; }
    public String getOriginalFileNm() { return originalFileNm; }
    public String getStoredFileNm() { return storedFileNm; }
}

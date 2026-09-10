package com.aonfine.ada.analysis;

/** success means the request/status read completed, not that the analysis itself finished. Mirrors UploadResult's convention. */
public class AnalysisResult {
    private final boolean success;
    private final String message;
    private final String code;
    private final String dumpId, jobId, attemptId, statusCd, failReason;
    private final int attemptNo;
    private final boolean terminal, retryAllowed, cancelAllowed, resultAvailable;

    private AnalysisResult(boolean success, String message, String code, String dumpId, String jobId, String attemptId,
            int attemptNo, String statusCd, String failReason, boolean terminal, boolean retryAllowed,
            boolean cancelAllowed, boolean resultAvailable) {
        this.success = success; this.message = message; this.code = code;
        this.dumpId = dumpId; this.jobId = jobId; this.attemptId = attemptId; this.attemptNo = attemptNo;
        this.statusCd = statusCd; this.failReason = failReason;
        this.terminal = terminal; this.retryAllowed = retryAllowed; this.cancelAllowed = cancelAllowed;
        this.resultAvailable = resultAvailable;
    }

    public static AnalysisResult fail(String code, String message) {
        return new AnalysisResult(false, message, code, null, null, null, 0, null, null, false, false, false, false);
    }

    public static AnalysisResult ok(String message) {
        return new AnalysisResult(true, message, null, null, null, null, 0, null, null, false, false, false, false);
    }

    public static AnalysisResult idle(String dumpId) {
        return new AnalysisResult(true, "분석을 실행한 적이 없습니다.", null, dumpId, null, null, 0,
                AnalysisState.IDLE.name(), null, false, false, false, false);
    }

    public static AnalysisResult of(String dumpId, AnalysisJobVO job, AnalysisAttemptVO attempt) {
        AnalysisState state = job.state();
        boolean terminal = state == AnalysisState.CANCELLED || state == AnalysisState.FAILED
                || state == AnalysisState.SUCCEEDED || state == AnalysisState.RECOVERY_REQUIRED;
        boolean retryAllowed = state.mayCreateAttempt();
        boolean cancelAllowed = state == AnalysisState.QUEUED || state == AnalysisState.RUNNING;
        boolean resultAvailable = state == AnalysisState.SUCCEEDED
                && attempt != null && attempt.getResultPath() != null && !attempt.getResultPath().isEmpty();
        String message = label(state);
        return new AnalysisResult(true, message, null, dumpId, job.getJobId(),
                attempt == null ? null : attempt.getAttemptId(), attempt == null ? 0 : attempt.getAttemptNo(),
                state.name(), attempt == null ? null : attempt.getFailReason(),
                terminal, retryAllowed, cancelAllowed, resultAvailable);
    }

    private static String label(AnalysisState state) {
        switch (state) {
            case QUEUED: return "분석 대기 중입니다.";
            case RUNNING: return "분석을 실행 중입니다.";
            case CANCEL_REQUESTED: return "분석 취소를 요청했습니다. 실행 중단을 확인 중입니다.";
            case CANCELLED: return "분석이 취소되었습니다. 다시 요청할 수 있습니다.";
            case FAILED: return "분석이 실패했습니다. 다시 요청할 수 있습니다.";
            case SUCCEEDED: return "분석이 완료되었습니다.";
            case RECOVERY_REQUIRED: return "이전 실행 종료 확인이 필요합니다. 관리자에게 문의해 주세요.";
            default: return "분석 준비 상태입니다.";
        }
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public String getCode() { return code; }
    public String getDumpId() { return dumpId; }
    public String getJobId() { return jobId; }
    public String getAttemptId() { return attemptId; }
    public int getAttemptNo() { return attemptNo; }
    public String getStatusCd() { return statusCd; }
    public String getFailReason() { return failReason; }
    public boolean isTerminal() { return terminal; }
    public boolean isRetryAllowed() { return retryAllowed; }
    public boolean isCancelAllowed() { return cancelAllowed; }
    public boolean isResultAvailable() { return resultAvailable; }
}

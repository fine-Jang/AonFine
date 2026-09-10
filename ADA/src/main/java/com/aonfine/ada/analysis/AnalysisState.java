package com.aonfine.ada.analysis;

/** Future contract only: no producer, consumer, Worker endpoint or execution service. */
public enum AnalysisState {
    IDLE, QUEUED, RUNNING, CANCEL_REQUESTED, CANCELLED, FAILED, SUCCEEDED, RECOVERY_REQUIRED;

    public boolean mayTransitionTo(AnalysisState next) {
        if (next == null) return false;
        switch (this) {
            case QUEUED: return next == RUNNING || next == CANCEL_REQUESTED || next == FAILED;
            case RUNNING: return next == CANCEL_REQUESTED || next == FAILED || next == SUCCEEDED || next == RECOVERY_REQUIRED;
            case CANCEL_REQUESTED: return next == CANCELLED || next == FAILED || next == RECOVERY_REQUIRED;
            // Recovery requires external proof the old execution has stopped. Never infer cancellation from a timeout.
            case RECOVERY_REQUIRED: return next == FAILED || next == CANCELLED;
            default: return false;
        }
    }
    public boolean mayCreateAttempt() { return this == IDLE || this == CANCELLED || this == FAILED; }

    public static boolean acceptsReport(String currentAttemptId, String reportedAttemptId, long currentVersion,
            long expectedVersion, AnalysisState current, AnalysisState next) {
        return currentAttemptId != null && currentAttemptId.equals(reportedAttemptId)
                && currentVersion == expectedVersion && current != null && current.mayTransitionTo(next);
    }
}

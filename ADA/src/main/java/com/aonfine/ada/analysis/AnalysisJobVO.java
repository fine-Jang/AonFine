package com.aonfine.ada.analysis;

import java.io.Serializable;

/** TB_ADA_ANALYSIS_JOB row. One JOB per DUMP_ID; CURRENT_ATTEMPT_ID/STATUS_CD/VERSION_NO are the CAS pointer. */
public class AnalysisJobVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String jobId;
    private String dumpId;
    private String currentAttemptId;
    private String statusCd;
    private long versionNo;
    private String requestedBy;
    private String regDt;
    private String modDt;

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }
    public String getDumpId() { return dumpId; }
    public void setDumpId(String dumpId) { this.dumpId = dumpId; }
    public String getCurrentAttemptId() { return currentAttemptId; }
    public void setCurrentAttemptId(String currentAttemptId) { this.currentAttemptId = currentAttemptId; }
    public String getStatusCd() { return statusCd; }
    public void setStatusCd(String statusCd) { this.statusCd = statusCd; }
    public long getVersionNo() { return versionNo; }
    public void setVersionNo(long versionNo) { this.versionNo = versionNo; }
    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }
    public String getRegDt() { return regDt; }
    public void setRegDt(String regDt) { this.regDt = regDt; }
    public String getModDt() { return modDt; }
    public void setModDt(String modDt) { this.modDt = modDt; }

    public AnalysisState state() { return AnalysisState.valueOf(statusCd); }
}

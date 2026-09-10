package com.aonfine.ada.analysis;

import java.io.Serializable;

/** TB_ADA_ANALYSIS_ATTEMPT row. One row per execution attempt; history is never overwritten. */
public class AnalysisAttemptVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String jobId;
    private String attemptId;
    private int attemptNo;
    private String requestKey;
    private String statusCd;
    private long versionNo;
    private String cancelRequestedYn;
    private String cancelRequestedBy;
    private String cancelRequestedDt;
    private String requestedBy;
    private String workerId;
    private String startDt;
    private String endDt;
    private String lastHeartbeatDt;
    private String failCode;
    private String failReason;
    private String resultPath;
    private String regDt;
    private String modDt;

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }
    public String getAttemptId() { return attemptId; }
    public void setAttemptId(String attemptId) { this.attemptId = attemptId; }
    public int getAttemptNo() { return attemptNo; }
    public void setAttemptNo(int attemptNo) { this.attemptNo = attemptNo; }
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String requestKey) { this.requestKey = requestKey; }
    public String getStatusCd() { return statusCd; }
    public void setStatusCd(String statusCd) { this.statusCd = statusCd; }
    public long getVersionNo() { return versionNo; }
    public void setVersionNo(long versionNo) { this.versionNo = versionNo; }
    public String getCancelRequestedYn() { return cancelRequestedYn; }
    public void setCancelRequestedYn(String cancelRequestedYn) { this.cancelRequestedYn = cancelRequestedYn; }
    public String getCancelRequestedBy() { return cancelRequestedBy; }
    public void setCancelRequestedBy(String cancelRequestedBy) { this.cancelRequestedBy = cancelRequestedBy; }
    public String getCancelRequestedDt() { return cancelRequestedDt; }
    public void setCancelRequestedDt(String cancelRequestedDt) { this.cancelRequestedDt = cancelRequestedDt; }
    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }
    public String getWorkerId() { return workerId; }
    public void setWorkerId(String workerId) { this.workerId = workerId; }
    public String getStartDt() { return startDt; }
    public void setStartDt(String startDt) { this.startDt = startDt; }
    public String getEndDt() { return endDt; }
    public void setEndDt(String endDt) { this.endDt = endDt; }
    public String getLastHeartbeatDt() { return lastHeartbeatDt; }
    public void setLastHeartbeatDt(String lastHeartbeatDt) { this.lastHeartbeatDt = lastHeartbeatDt; }
    public String getFailCode() { return failCode; }
    public void setFailCode(String failCode) { this.failCode = failCode; }
    public String getFailReason() { return failReason; }
    public void setFailReason(String failReason) { this.failReason = failReason; }
    public String getResultPath() { return resultPath; }
    public void setResultPath(String resultPath) { this.resultPath = resultPath; }
    public String getRegDt() { return regDt; }
    public void setRegDt(String regDt) { this.regDt = regDt; }
    public String getModDt() { return modDt; }
    public void setModDt(String modDt) { this.modDt = modDt; }

    public AnalysisState state() { return AnalysisState.valueOf(statusCd); }
}

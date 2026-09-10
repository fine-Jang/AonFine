package com.aonfine.adaworker.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Mirrors com.aonfine.ada.worker.WorkerClaimResult's JSON shape. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClaimResponse {
    public boolean success;
    public boolean available;
    public String message;
    public String jobId;
    public String attemptId;
    public String workerId;
    public int attemptNo;
    public long attemptVersionNo;
    public long jobVersionNo;
    public String dumpId;
    public String dumpType;
    public String deptNm;
    public String taskNm;
    public String hostNm;
    public String originalFileNm;
    public String storedFileNm;
}

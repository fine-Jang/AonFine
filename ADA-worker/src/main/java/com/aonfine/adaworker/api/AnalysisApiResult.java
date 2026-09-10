package com.aonfine.adaworker.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Mirrors com.aonfine.ada.analysis.AnalysisResult's JSON shape (used for heartbeat/report responses). */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnalysisApiResult {
    public boolean success;
    public String message;
    public String code;
    public String dumpId;
    public String jobId;
    public String attemptId;
    public int attemptNo;
    public String statusCd;
    public String failReason;
    public boolean terminal;
    public boolean retryAllowed;
    public boolean cancelAllowed;
    public boolean resultAvailable;
}

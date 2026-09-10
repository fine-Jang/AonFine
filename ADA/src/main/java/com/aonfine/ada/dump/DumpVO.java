package com.aonfine.ada.dump;

import java.io.Serializable;

public class DumpVO implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final String STATUS_UPLOADING = "UPLOADING";
    public static final String STATUS_VALIDATING = "VALIDATING";
    public static final String STATUS_STORED = "STORED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_EXPIRED = "EXPIRED";
    public static final String STATUS_READY = "UPLOAD_READY";
    public static final String STATUS_CANCEL_REQUESTED = "CANCEL_REQUESTED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_FINALIZING = "FINALIZING";
    public static final String STATUS_CLEANUP_PENDING = "CLEANUP_PENDING";
    public static final String STATUS_CLEANUP_FAILED = "CLEANUP_FAILED";

    public static final String ANALYSIS_NOT_ANALYZED = "NOT_ANALYZED";
    public static final String ANALYSIS_ANALYZING = "ANALYZING";
    public static final String ANALYSIS_ANALYZED = "ANALYZED";
    public static final String ANALYSIS_FAILED = "ANALYSIS_FAILED";

    public static final String DUMP_TYPE_HEAP = "HEAP";
    public static final String DUMP_TYPE_THREAD = "THREAD";

    public static boolean isValidDumpType(String dumpType) {
        return DUMP_TYPE_HEAP.equals(dumpType) || DUMP_TYPE_THREAD.equals(dumpType);
    }

    private String dumpId;
    private String userId;
    private String dumpType;
    private String deptNm;
    private String taskNm;
    private String hostNm;
    private String originalFileNm;
    private String storedFileNm;
    private long fileSize;
    private String statusCd;
    private String analysisStatusCd;
    private String failReason;
    private String expiredYn;
    private String regDt;
    private String modDt;

    /** 조회 시점에 REG_DT + 보관일수 기준으로 재계산한 만료 여부 (EXPIRED_YN 컬럼과 별개, 즉시성 보장용) */
    private boolean expiredNow;

    public String getDumpId() {
        return dumpId;
    }

    public void setDumpId(String dumpId) {
        this.dumpId = dumpId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getDeptNm() {
        return deptNm;
    }

    public void setDeptNm(String deptNm) {
        this.deptNm = deptNm;
    }

    public String getTaskNm() {
        return taskNm;
    }

    public void setTaskNm(String taskNm) {
        this.taskNm = taskNm;
    }

    public String getHostNm() {
        return hostNm;
    }

    public void setHostNm(String hostNm) {
        this.hostNm = hostNm;
    }

    public String getDumpType() {
        return dumpType;
    }

    public void setDumpType(String dumpType) {
        this.dumpType = dumpType;
    }

    /** 화면 표시용 (JSP EL에서 dump.dumpTypeLabel로 사용). */
    public String getDumpTypeLabel() {
        if (DUMP_TYPE_HEAP.equals(dumpType)) {
            return "Heap";
        }
        if (DUMP_TYPE_THREAD.equals(dumpType)) {
            return "Thread";
        }
        return dumpType;
    }

    public String getOriginalFileNm() {
        return originalFileNm;
    }

    public void setOriginalFileNm(String originalFileNm) {
        this.originalFileNm = originalFileNm;
    }

    public String getStoredFileNm() {
        return storedFileNm;
    }

    public void setStoredFileNm(String storedFileNm) {
        this.storedFileNm = storedFileNm;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getStatusCd() {
        return statusCd;
    }

    public void setStatusCd(String statusCd) {
        this.statusCd = statusCd;
    }

    public String getAnalysisStatusCd() {
        return analysisStatusCd;
    }

    public void setAnalysisStatusCd(String analysisStatusCd) {
        this.analysisStatusCd = analysisStatusCd;
    }

    public String getFailReason() {
        return failReason;
    }

    public void setFailReason(String failReason) {
        this.failReason = failReason;
    }

    public String getExpiredYn() {
        return expiredYn;
    }

    public void setExpiredYn(String expiredYn) {
        this.expiredYn = expiredYn;
    }

    public String getRegDt() {
        return regDt;
    }

    public void setRegDt(String regDt) {
        this.regDt = regDt;
    }

    public String getModDt() {
        return modDt;
    }

    public void setModDt(String modDt) {
        this.modDt = modDt;
    }

    public boolean isExpiredNow() {
        return expiredNow;
    }

    public void setExpiredNow(boolean expiredNow) {
        this.expiredNow = expiredNow;
    }

    /** 화면 표시용 (JSP EL에서 dump.fileSizeLabel로 사용). */
    public String getFileSizeLabel() {
        double gb = fileSize / (1024.0 * 1024.0 * 1024.0);
        if (gb >= 1) {
            return String.format("%.2f GB", gb);
        }
        double mb = fileSize / (1024.0 * 1024.0);
        if (mb >= 1) {
            return String.format("%.1f MB", mb);
        }
        return String.format("%.0f KB", fileSize / 1024.0);
    }

    /** 화면 표시용 상태 라벨 (JSP EL에서 dump.statusLabel로 사용). */
    public String getStatusLabel() {
        if ((expiredNow && STATUS_STORED.equals(statusCd)) || STATUS_EXPIRED.equals(statusCd)) {
            return "보관 만료";
        }
        switch (statusCd == null ? "" : statusCd) {
            case STATUS_READY: return "업로드 대기";
            case STATUS_CANCEL_REQUESTED: return "업로드 취소 요청 중";
            case STATUS_CANCELLED: return "업로드 취소 완료";
            case STATUS_FINALIZING: return "저장 확정 중";
            case STATUS_CLEANUP_PENDING: return "부분파일 정리 중";
            case STATUS_CLEANUP_FAILED: return "부분파일 정리 실패";
            case STATUS_UPLOADING:
                return "업로드 중";
            case STATUS_VALIDATING:
                return "검증 중";
            case STATUS_STORED:
                return analysisStatusLabelInternal();
            case STATUS_FAILED:
                return "업로드 실패";
            default:
                return statusCd;
        }
    }

    private String analysisStatusLabelInternal() {
        switch (analysisStatusCd == null ? "" : analysisStatusCd) {
            case "QUEUED": return "분석 대기";
            case "RUNNING": return "분석 실행 중";
            case "CANCEL_REQUESTED": return "분석 취소 요청 중";
            case "CANCELLED": return "분석 취소 완료";
            case "FAILED": return "분석 실패";
            case "SUCCEEDED": return "분석 완료";
            case "RECOVERY_REQUIRED": return "분석 실행 확인 필요";
            case ANALYSIS_ANALYZING:
                return "분석 중";
            case ANALYSIS_ANALYZED:
                return "분석 완료";
            case ANALYSIS_FAILED:
                return "분석 실패";
            default:
                return "분석 전";
        }
    }

    /** CSS 클래스 선택용 (status-STORED 등). */
    public String getStatusCssKey() {
        if ((expiredNow && STATUS_STORED.equals(statusCd)) || STATUS_EXPIRED.equals(statusCd)) {
            return STATUS_EXPIRED;
        }
        if (STATUS_STORED.equals(statusCd) && analysisStatusCd != null
                && !ANALYSIS_NOT_ANALYZED.equals(analysisStatusCd)) {
            return analysisStatusCd;
        }
        return statusCd;
    }
}

package com.aonfine.ada.common;

import java.io.File;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * ADA 저장소 경로 및 정책 값. 실제 값은 ada.properties에서 주입되며,
 * 운영 환경에서 NFS 마운트 경로/보관 정책을 코드 변경 없이 조정할 수 있게 한다.
 */
@Component
public class AdaProperties {

    @Value("${ada.storage.root}")
    private String storageRoot;

    @Value("${ada.storage.nfsMarkerFile}")
    private String nfsMarkerFile;

    @Value("${ada.upload.maxFileSizeBytes}")
    private long maxFileSizeBytes;

    @Value("${ada.upload.minFreeSpaceReserveBytes}")
    private long minFreeSpaceReserveBytes;

    @Value("${ada.upload.spaceCheckIntervalBytes}")
    private long spaceCheckIntervalBytes;

    @Value("${ada.validate.maxTarEntries}")
    private int maxTarEntries;

    @Value("${ada.validate.maxDecompressedTotalBytes}")
    private long maxDecompressedTotalBytes;

    @Value("${ada.validate.maxCompressionRatio}")
    private long maxCompressionRatio;

    @Value("${ada.validate.maxValidationSeconds}")
    private int maxValidationSeconds;

    @Value("${ada.retention.days}")
    private int retentionDays;

    @Value("${ada.retention.cleanupIntervalMinutes}")
    private int cleanupIntervalMinutes;

    @Value("${ada.analysis.heartbeatTimeoutSeconds}")
    private int analysisHeartbeatTimeoutSeconds;

    @Value("${ada.storage.resultRoot}")
    private String resultRoot;

    public File getStagingDir() {
        return new File(storageRoot, "incoming");
    }

    public File getStoreDir() {
        return new File(storageRoot, "store");
    }

    public File getNfsMarkerFile() {
        return new File(nfsMarkerFile);
    }

    public String getStorageRoot() {
        return storageRoot;
    }

    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public long getMinFreeSpaceReserveBytes() {
        return minFreeSpaceReserveBytes;
    }

    public long getSpaceCheckIntervalBytes() {
        return spaceCheckIntervalBytes;
    }

    public int getMaxTarEntries() {
        return maxTarEntries;
    }

    public long getMaxDecompressedTotalBytes() {
        return maxDecompressedTotalBytes;
    }

    public long getMaxCompressionRatio() {
        return maxCompressionRatio;
    }

    public int getMaxValidationSeconds() {
        return maxValidationSeconds;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public int getCleanupIntervalMinutes() {
        return cleanupIntervalMinutes;
    }

    public int getAnalysisHeartbeatTimeoutSeconds() {
        return analysisHeartbeatTimeoutSeconds;
    }

    /** Worker writes analysis deliverables here; ADA only ever reads from it. */
    public File getResultDir() {
        return new File(resultRoot);
    }

    // 아래 setter들은 운영 중 Spring @Value 주입 경로에서는 쓰이지 않고,
    // 단위 테스트에서 Spring 컨텍스트 없이 값을 채워 넣기 위해 존재한다.

    public void setStorageRoot(String storageRoot) {
        this.storageRoot = storageRoot;
    }

    public void setNfsMarkerFile(String nfsMarkerFile) {
        this.nfsMarkerFile = nfsMarkerFile;
    }

    public void setMaxFileSizeBytes(long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    public void setMinFreeSpaceReserveBytes(long minFreeSpaceReserveBytes) {
        this.minFreeSpaceReserveBytes = minFreeSpaceReserveBytes;
    }

    public void setSpaceCheckIntervalBytes(long spaceCheckIntervalBytes) {
        this.spaceCheckIntervalBytes = spaceCheckIntervalBytes;
    }

    public void setMaxTarEntries(int maxTarEntries) {
        this.maxTarEntries = maxTarEntries;
    }

    public void setMaxDecompressedTotalBytes(long maxDecompressedTotalBytes) {
        this.maxDecompressedTotalBytes = maxDecompressedTotalBytes;
    }

    public void setMaxCompressionRatio(long maxCompressionRatio) {
        this.maxCompressionRatio = maxCompressionRatio;
    }

    public void setMaxValidationSeconds(int maxValidationSeconds) {
        this.maxValidationSeconds = maxValidationSeconds;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    public void setCleanupIntervalMinutes(int cleanupIntervalMinutes) {
        this.cleanupIntervalMinutes = cleanupIntervalMinutes;
    }

    public void setAnalysisHeartbeatTimeoutSeconds(int analysisHeartbeatTimeoutSeconds) {
        this.analysisHeartbeatTimeoutSeconds = analysisHeartbeatTimeoutSeconds;
    }

    public void setResultRoot(String resultRoot) {
        this.resultRoot = resultRoot;
    }
}

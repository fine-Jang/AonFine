package com.aonfine.adaworker.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Worker configuration. The API token is deliberately NOT read from the properties file by
 * default -- it must come from the ADA_WORKER_API_TOKEN environment variable (or, if present in
 * the file, that is only a local-dev fallback and is never logged). Never printed, never passed
 * as a CLI argument to any subprocess, never written into a report.
 */
public final class WorkerConfig {
    public String apiBaseUrl;
    public String apiToken;
    public String workerId;
    public int pollIntervalSeconds = 15;
    public int heartbeatIntervalSeconds = 30;
    public int claimHttpTimeoutSeconds = 20;

    public String matBatchScript = "/DATA/Work/bin/run-mat-batch.sh";
    public long matTimeoutSeconds = 1800;

    public String claudeExecutable = "claude";
    public String claudeModel = "sonnet";
    public double claudeMaxBudgetUsd = 1.0;
    public long claudeTimeoutSeconds = 600;

    public String workRootDir = "/DATA/Work/attempts";
    public String storageRootDir = "/DATA/ada/store"; // where the Worker reads the original tar.gz from (same NFS export ADA writes to)
    /** Deliverables go here, not into the Worker's private scratch: ADA (jboss) must be able to read them to serve downloads. */
    public String resultRootDir = "/DATA/ada/results";

    public int extractionMaxEntries = 200_000;
    public long extractionMaxDecompressedBytes = 107_374_182_400L;
    public long extractionMaxCompressionRatio = 300;
    public long extractionMaxMillis = 30 * 60 * 1000L;

    public static WorkerConfig load(Path propertiesFile) throws IOException {
        Properties props = new Properties();
        if (propertiesFile != null && Files.isRegularFile(propertiesFile)) {
            try (InputStream in = Files.newInputStream(propertiesFile)) {
                props.load(in);
            }
        }
        WorkerConfig config = new WorkerConfig();
        config.apiBaseUrl = props.getProperty("ada.api.baseUrl", config.apiBaseUrl);
        config.workerId = props.getProperty("worker.id", System.getenv().getOrDefault("HOSTNAME", "worker-1"));
        config.pollIntervalSeconds = intProp(props, "worker.pollIntervalSeconds", config.pollIntervalSeconds);
        config.heartbeatIntervalSeconds = intProp(props, "worker.heartbeatIntervalSeconds", config.heartbeatIntervalSeconds);
        config.matBatchScript = props.getProperty("mat.batchScript", config.matBatchScript);
        config.matTimeoutSeconds = longProp(props, "mat.timeoutSeconds", config.matTimeoutSeconds);
        config.claudeExecutable = props.getProperty("claude.executable", config.claudeExecutable);
        config.claudeModel = props.getProperty("claude.model", config.claudeModel);
        config.claudeMaxBudgetUsd = doubleProp(props, "claude.maxBudgetUsd", config.claudeMaxBudgetUsd);
        config.claudeTimeoutSeconds = longProp(props, "claude.timeoutSeconds", config.claudeTimeoutSeconds);
        config.workRootDir = props.getProperty("worker.workRootDir", config.workRootDir);
        config.storageRootDir = props.getProperty("ada.storageRootDir", config.storageRootDir);
        config.resultRootDir = props.getProperty("ada.resultRootDir", config.resultRootDir);

        // API token: environment variable wins; the properties file value (if any) is a local-dev-only fallback.
        String envToken = System.getenv("ADA_WORKER_API_TOKEN");
        config.apiToken = envToken != null ? envToken : props.getProperty("ada.api.token");
        return config;
    }

    private static int intProp(Properties p, String key, int fallback) {
        String v = p.getProperty(key);
        return v == null ? fallback : Integer.parseInt(v.trim());
    }
    private static long longProp(Properties p, String key, long fallback) {
        String v = p.getProperty(key);
        return v == null ? fallback : Long.parseLong(v.trim());
    }
    private static double doubleProp(Properties p, String key, double fallback) {
        String v = p.getProperty(key);
        return v == null ? fallback : Double.parseDouble(v.trim());
    }
}

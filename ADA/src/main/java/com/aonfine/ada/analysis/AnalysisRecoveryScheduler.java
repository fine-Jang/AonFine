package com.aonfine.ada.analysis;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.aonfine.ada.common.AdaProperties;

/**
 * Heartbeat-silence detector, not a completion detector. A RUNNING/CANCEL_REQUESTED attempt whose
 * heartbeat is older than the timeout moves to RECOVERY_REQUIRED only -- never FAILED/CANCELLED --
 * because silence is not proof the Worker process actually stopped (API-CONTRACT.md point 6).
 * RECOVERY_REQUIRED is only cleared by a worker's own report.do once it can attest the process ended.
 */
@Component
public class AnalysisRecoveryScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnalysisRecoveryScheduler.class);
    private static final int BATCH_LIMIT = 100;

    @Resource private AdaProperties adaProperties;
    @Resource(name = "analysisService") private AnalysisService analysisService;

    private ScheduledExecutorService executor;

    @PostConstruct
    public void start() {
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ada-analysis-recovery");
            t.setDaemon(true);
            return t;
        });
        long intervalSeconds = Math.max(30, adaProperties.getAnalysisHeartbeatTimeoutSeconds() / 2);
        executor.scheduleWithFixedDelay(this::runSafely, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        LOGGER.info("ADA analysis recovery scheduler started, intervalSeconds={}, heartbeatTimeoutSeconds={}",
                intervalSeconds, adaProperties.getAnalysisHeartbeatTimeoutSeconds());
    }

    @PreDestroy
    public void stop() {
        if (executor != null) executor.shutdownNow();
    }

    private void runSafely() {
        try {
            int recovered = analysisService.recoverStaleHeartbeats(adaProperties.getAnalysisHeartbeatTimeoutSeconds(), BATCH_LIMIT);
            if (recovered > 0) LOGGER.warn("ADA analysis recovery cycle: {} attempt(s) marked RECOVERY_REQUIRED", recovered);
        } catch (Exception e) {
            LOGGER.error("ADA analysis recovery cycle failed, will retry next cycle", e);
        }
    }
}

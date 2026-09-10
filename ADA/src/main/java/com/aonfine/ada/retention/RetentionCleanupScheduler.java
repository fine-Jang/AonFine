package com.aonfine.ada.retention;

import java.io.File;
import java.util.List;
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
import com.aonfine.ada.common.NfsMountGuard;
import com.aonfine.ada.dump.DumpService;
import com.aonfine.ada.dump.DumpVO;

/**
 * 업로드 완료(REG_DT) 후 보관기간(기본 30일)이 지난 원본 파일을 주기적으로 정리한다.
 *
 * - DB 메타정보는 삭제하지 않고 STATUS_CD=EXPIRED, EXPIRED_YN=Y로만 표시한다.
 * - 파일 삭제는 항상 정해진 저장 디렉터리 하위 경로인지 확인한 뒤에만 수행한다.
 * - 삭제/DB 갱신이 한 건이라도 실패하면 그 건은 다음 주기에 자연스럽게 다시 후보로 잡혀 재시도된다
 *   (파일이 이미 없으면 "이미 정리된 것"으로 간주하고 DB만 갱신하므로 재시도는 멱등적이다).
 * - 애플리케이션 기동 시 즉시 1회 실행하여, 앱이 중지되어 있던 기간의 정리도 재시작 후 따라잡는다.
 * - 다운로드 가능 여부는 이 배치와 별개로 매 요청마다 즉시 재계산된다({@link DumpService#getForDownload}).
 */
@Component
public class RetentionCleanupScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RetentionCleanupScheduler.class);
    private static final int BATCH_LIMIT = 200;

    @Resource
    private AdaProperties adaProperties;

    @Resource(name = "dumpService")
    private DumpService dumpService;

    @Resource
    private NfsMountGuard nfsMountGuard;

    private ScheduledExecutorService executor;

    @PostConstruct
    public void start() {
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ada-retention-cleanup");
            t.setDaemon(true);
            return t;
        });
        long intervalMinutes = Math.max(1, adaProperties.getCleanupIntervalMinutes());
        // initialDelay=0: 기동 즉시 1회 실행하여 정지 기간 중 만료된 건도 재시작 후 바로 정리한다.
        executor.scheduleWithFixedDelay(this::runSafely, 0, intervalMinutes, TimeUnit.MINUTES);
        LOGGER.info("ADA retention cleanup scheduler started, intervalMinutes={}", intervalMinutes);
    }

    @PreDestroy
    public void stop() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private void runSafely() {
        try {
            run();
        } catch (Exception e) {
            LOGGER.error("ADA retention cleanup cycle failed, will retry next cycle", e);
        }
    }

    private void run() {
        try {
            nfsMountGuard.assertMounted();
        } catch (Exception e) {
            LOGGER.warn("ADA retention cleanup skipped this cycle: storage not mounted");
            return;
        }

        File storeDir = adaProperties.getStoreDir();
        List<DumpVO> candidates = dumpService.selectExpiredCandidates(BATCH_LIMIT);
        if (candidates.isEmpty()) {
            return;
        }
        LOGGER.info("ADA retention cleanup: {} candidate(s) past {} day(s)", candidates.size(),
                adaProperties.getRetentionDays());

        for (DumpVO vo : candidates) {
            try {
                cleanupOne(storeDir, vo);
            } catch (Exception e) {
                LOGGER.error("failed to clean up dumpId={}, will retry next cycle", vo.getDumpId(), e);
            }
        }
    }

    private void cleanupOne(File storeDir, DumpVO vo) throws Exception {
        File target = new File(storeDir, vo.getStoredFileNm());
        // 방어적 확인: 저장 디렉터리 바깥을 절대 건드리지 않는다.
        if (!target.getCanonicalPath().startsWith(storeDir.getCanonicalPath() + File.separator)) {
            LOGGER.error("refusing to delete file outside store dir for dumpId={}: {}", vo.getDumpId(), target);
            return;
        }
        if (target.exists() && !target.delete()) {
            LOGGER.warn("could not delete expired file for dumpId={}: {}", vo.getDumpId(), target);
            return; // 삭제 실패 시 DB는 그대로 두어 다음 주기에 재시도되게 한다.
        }
        dumpService.markCleaned(vo.getDumpId());
        LOGGER.info("cleaned up expired dump {}", vo.getDumpId());
    }
}

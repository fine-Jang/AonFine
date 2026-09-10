package com.aonfine.adaworker;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aonfine.adaworker.api.AdaApiClient;
import com.aonfine.adaworker.api.ClaimResponse;
import com.aonfine.adaworker.claude.ClaudeCliInvoker;
import com.aonfine.adaworker.config.WorkerConfig;
import com.aonfine.adaworker.evidence.EvidenceBundle;
import com.aonfine.adaworker.evidence.EvidenceMasker;
import com.aonfine.adaworker.extract.SafeTarGzExtractor;
import com.aonfine.adaworker.log.ServerLogCorrelator;
import com.aonfine.adaworker.mat.HeapOverview;
import com.aonfine.adaworker.mat.LeakSuspect;
import com.aonfine.adaworker.mat.MatReportParser;
import com.aonfine.adaworker.mat.MatRunner;
import com.aonfine.adaworker.report.ReportAssembler;
import com.aonfine.adaworker.threaddump.ThreadDumpEntry;
import com.aonfine.adaworker.threaddump.ThreadDumpParser;
import com.aonfine.adaworker.util.SimpleZipExtractor;
import com.aonfine.adaworker.util.SimpleZipWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Worker entry point: claim one QUEUED attempt at a time, extract the dump, run MAT/parse thread
 * dumps, correlate logs, draft the interpretive section via Claude Code CLI, assemble the report,
 * and report the outcome back to ADA's internal Worker API. See API-CONTRACT.md for the state
 * machine this implements against.
 */
public final class WorkerMain {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorkerMain.class);
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public static void main(String[] args) throws Exception {
        Path configPath = args.length > 0 ? Path.of(args[0]) : Path.of("/DATA/Work/etc/worker.properties");
        WorkerConfig config = WorkerConfig.load(configPath);
        if (config.apiToken == null || config.apiToken.isBlank()) {
            LOGGER.error("ADA_WORKER_API_TOKEN is not set; refusing to start (fail closed, same posture as the server side)");
            System.exit(1);
        }
        if (config.apiBaseUrl == null || config.apiBaseUrl.isBlank()) {
            LOGGER.error("ada.api.baseUrl is not configured; refusing to start");
            System.exit(1);
        }
        AdaApiClient api = new AdaApiClient(config.apiBaseUrl, config.apiToken, config.claimHttpTimeoutSeconds);
        new WorkerMain(config, api).runForever();
    }

    private final WorkerConfig config;
    private final AdaApiClient api;
    private final MatRunner matRunner;
    private final MatReportParser matReportParser = new MatReportParser();
    private final ThreadDumpParser threadDumpParser = new ThreadDumpParser();
    private final ServerLogCorrelator logCorrelator = new ServerLogCorrelator();
    private final ReportAssembler reportAssembler = new ReportAssembler();
    private final ClaudeCliInvoker claudeInvoker;

    WorkerMain(WorkerConfig config, AdaApiClient api) {
        this.config = config;
        this.api = api;
        this.matRunner = new MatRunner(config.matBatchScript, config.matTimeoutSeconds);
        ClaudeCliInvoker.ClaudeConfig cc = new ClaudeCliInvoker.ClaudeConfig();
        cc.claudeExecutable = config.claudeExecutable;
        cc.model = config.claudeModel;
        cc.maxBudgetUsd = config.claudeMaxBudgetUsd;
        cc.timeoutSeconds = config.claudeTimeoutSeconds;
        this.claudeInvoker = new ClaudeCliInvoker(cc);
    }

    void runForever() {
        LOGGER.info("ADA Worker starting: workerId={}, apiBaseUrl={}", config.workerId, config.apiBaseUrl);
        while (true) {
            try {
                ClaimResponse claim = api.claim(config.workerId);
                if (claim.available) {
                    LOGGER.info("claimed attemptId={} jobId={} dumpId={}", claim.attemptId, claim.jobId, claim.dumpId);
                    processOneAttempt(claim);
                } else {
                    Thread.sleep(config.pollIntervalSeconds * 1000L);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                LOGGER.error("poll/claim cycle failed, retrying after pollIntervalSeconds", e);
                sleepQuietly(config.pollIntervalSeconds * 1000L);
            }
        }
    }

    private void processOneAttempt(ClaimResponse claim) {
        AtomicBoolean lostOwnership = new AtomicBoolean(false);
        ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            try {
                var hb = api.heartbeat(claim.attemptId, config.workerId);
                if (!hb.success) {
                    LOGGER.warn("heartbeat rejected for attemptId={}: {}", claim.attemptId, hb.message);
                    lostOwnership.set(true);
                }
            } catch (Exception e) {
                LOGGER.warn("heartbeat call failed for attemptId={} (network issue, not necessarily ownership loss)", claim.attemptId, e);
            }
        }, config.heartbeatIntervalSeconds, config.heartbeatIntervalSeconds, TimeUnit.SECONDS);

        File attemptDir = new File(config.workRootDir, claim.attemptId);
        // Deliverables live under the shared result root (readable by ADA/jboss), never in the Worker's private scratch.
        File attemptResultRoot = new File(config.resultRootDir, claim.attemptId);
        try {
            Files.createDirectories(attemptDir.toPath());
            File extractDir = new File(attemptDir, "extract");
            File resultDir = new File(attemptResultRoot, "result");
            Files.createDirectories(extractDir.toPath());
            Files.createDirectories(resultDir.toPath());

            File original = new File(config.storageRootDir, claim.storedFileNm);
            if (!original.isFile()) {
                reportFailure(claim, "SOURCE_MISSING", "원본 파일을 찾을 수 없습니다: " + original);
                return;
            }
            if (lostOwnership.get()) return;

            SafeTarGzExtractor.Limits limits = new SafeTarGzExtractor.Limits();
            limits.maxEntries = config.extractionMaxEntries;
            limits.maxDecompressedTotalBytes = config.extractionMaxDecompressedBytes;
            limits.maxCompressionRatio = config.extractionMaxCompressionRatio;
            limits.maxExtractionMillis = config.extractionMaxMillis;
            List<SafeTarGzExtractor.ExtractedFile> files = new SafeTarGzExtractor(limits).extract(original, extractDir);

            EvidenceBundle bundle = new EvidenceBundle();
            bundle.metadata.put("dumpId", claim.dumpId);
            bundle.metadata.put("deptNm", claim.deptNm);
            bundle.metadata.put("taskNm", claim.taskNm);
            bundle.metadata.put("hostNm", claim.hostNm);
            bundle.metadata.put("originalFileNm", claim.originalFileNm);

            List<ReportAssembler.ChartImage> charts = new ArrayList<>();
            String candidateLogTimestamp = null;

            for (SafeTarGzExtractor.ExtractedFile f : files) {
                if (SafeTarGzExtractor.isHprof(f.entryName)) {
                    if (lostOwnership.get()) return;
                    EvidenceBundle.DumpEvidence dumpEvidence = analyzeHeapDump(f, extractDir, charts);
                    bundle.dumps.add(dumpEvidence);
                    if (candidateLogTimestamp == null && dumpEvidence.heapOverview != null) {
                        candidateLogTimestamp = extractTimestamp(dumpEvidence.heapOverview);
                    }
                }
            }
            for (SafeTarGzExtractor.ExtractedFile f : files) {
                if (SafeTarGzExtractor.isHprof(f.entryName)) continue;
                if (looksLikeThreadDump(f.file)) {
                    bundle.dumps.add(analyzeThreadDump(f));
                }
            }
            File serverLogFile = pickServerLogCandidate(files, extractDir);
            if (serverLogFile != null) {
                bundle.serverLog = correlateServerLog(serverLogFile, candidateLogTimestamp);
            }

            if (lostOwnership.get()) return;

            String evidenceJson = JSON.writeValueAsString(bundle);
            String instructions = buildClaudeInstructions();
            // cwd = scratch dir, so the CLI's own stdout/stderr logs never end up inside the user-facing package.
            ClaudeCliInvoker.ClaudeResult claudeResult = claudeInvoker.draftReport(instructions, evidenceJson, attemptDir);

            if (claudeResult.isError || claudeResult.processTimedOut) {
                String reason = claudeResult.processTimedOut ? "Claude CLI 호출이 시간 제한을 초과했습니다."
                        : "Claude CLI 보고서 초안 생성에 실패했습니다: " + claudeResult.parseErrorMessage;
                LOGGER.error("claude drafting failed for attemptId={}: {}", claim.attemptId, reason);
                reportFailure(claim, "CLAUDE_DRAFT_FAILED", reason);
                return;
            }

            File evidenceDir = new File(resultDir, "evidence");
            Files.createDirectories(evidenceDir.toPath());
            Files.writeString(new File(evidenceDir, "evidence.json").toPath(), evidenceJson);

            File reportMd = reportAssembler.assemble(bundle, claudeResult.resultText, charts, resultDir);
            File resultZip = new File(attemptResultRoot, "result.zip");
            SimpleZipWriter.zipDirectory(resultDir, resultZip);

            if (lostOwnership.get()) return;
            LOGGER.info("analysis completed for attemptId={}: report={} zip={}", claim.attemptId, reportMd.getName(), resultZip);
            reportSuccess(claim, claim.attemptId + "/result.zip");
        } catch (Exception e) {
            LOGGER.error("processing failed for attemptId={}", claim.attemptId, e);
            if (!lostOwnership.get()) {
                reportFailure(claim, "WORKER_EXCEPTION", String.valueOf(e.getMessage()));
            }
        } finally {
            heartbeatExecutor.shutdownNow();
        }
    }

    private EvidenceBundle.DumpEvidence analyzeHeapDump(SafeTarGzExtractor.ExtractedFile f, File extractDir,
            List<ReportAssembler.ChartImage> charts) {
        EvidenceBundle.DumpEvidence dump = new EvidenceBundle.DumpEvidence();
        dump.dumpIdentifier = f.entryName;
        dump.dumpKind = "HEAP";
        try {
            File matCwd = f.file.getParentFile();
            MatRunner.MatRunResult matResult = matRunner.run(f.file, matCwd);
            if (matResult.exitCode != 0 || matResult.leakSuspectsZip == null || matResult.systemOverviewZip == null) {
                dump.note = "MAT 배치 분석이 완료되지 못했습니다 (exitCode=" + matResult.exitCode + "). 로그: " + trim(matResult.logTail, 500);
                return dump;
            }
            File overviewExtract = new File(matCwd, "report-overview");
            File leakExtract = new File(matCwd, "report-leaks");
            SimpleZipExtractor.extract(matResult.systemOverviewZip, overviewExtract);
            SimpleZipExtractor.extract(matResult.leakSuspectsZip, leakExtract);

            HeapOverview overview = matReportParser.parseHeapOverview(new File(overviewExtract, "index.html"));
            dump.heapOverview = overview.fields;

            List<LeakSuspect> suspects = matReportParser.parseLeakSuspects(new File(leakExtract, "index.html"));
            dump.leakSuspects = new ArrayList<>();
            for (LeakSuspect s : suspects) {
                EvidenceBundle.LeakSuspectEvidence e = new EvidenceBundle.LeakSuspectEvidence();
                e.suspectIdentifier = f.entryName + "#" + s.title.replace(" ", "");
                e.title = s.title;
                e.holderIdentity = s.holderIdentity;
                e.threadName = s.threadName;
                e.retainedBytes = s.retainedBytes;
                e.retainedPercent = s.retainedPercent;
                e.accumulationPointClass = s.accumulationPointClass;
                e.accumulationPointBytes = s.accumulationPointBytes;
                e.accumulationPointPercent = s.accumulationPointPercent;
                e.keywords = s.keywords;
                e.significantStackFrames = s.significantStackFrames;
                if (s.stackTraceHref != null) {
                    File stackPage = new File(leakExtract, s.stackTraceHref);
                    if (stackPage.isFile()) {
                        e.fullStackTrace = matReportParser.parseStackTracePage(stackPage);
                    }
                }
                if (e.fullStackTrace == null || e.fullStackTrace.isEmpty()) {
                    e.stackExtractionNote = "이 힙덤프에서 해당 점유 대상의 실행 스택을 추출하지 못했습니다(스택 정보가 덤프에 없거나 파싱 실패).";
                }
                dump.leakSuspects.add(e);
            }
            File topChart = findTopLevelChart(leakExtract);
            if (topChart != null) {
                ReportAssembler.ChartImage chart = new ReportAssembler.ChartImage();
                chart.dumpIdentifier = f.entryName;
                chart.sourceFile = topChart;
                chart.caption = "Heap Dump Overview -- " + f.entryName;
                charts.add(chart);
            }
        } catch (Exception e) {
            LOGGER.error("MAT analysis failed for {}", f.entryName, e);
            dump.note = "MAT 분석 중 오류가 발생했습니다: " + e.getMessage();
        }
        return dump;
    }

    private EvidenceBundle.DumpEvidence analyzeThreadDump(SafeTarGzExtractor.ExtractedFile f) {
        EvidenceBundle.DumpEvidence dump = new EvidenceBundle.DumpEvidence();
        dump.dumpIdentifier = f.entryName;
        dump.dumpKind = "THREAD";
        try {
            ThreadDumpParser.ParseResult parsed = threadDumpParser.parse(f.file.toPath());
            dump.threads = new ArrayList<>();
            for (ThreadDumpEntry t : parsed.threads) {
                EvidenceBundle.ThreadEvidence te = new EvidenceBundle.ThreadEvidence();
                te.threadIdentifier = f.entryName + "#" + t.name;
                te.name = t.name;
                te.state = t.state;
                te.stackFrames = t.stackFrames;
                dump.threads.add(te);
            }
            dump.deadlockCycles = parsed.deadlockCycles;
        } catch (Exception e) {
            dump.note = "스레드 덤프 파싱 중 오류: " + e.getMessage();
        }
        return dump;
    }

    private EvidenceBundle.LogEvidence correlateServerLog(File logFile, String candidateTimestamp) {
        EvidenceBundle.LogEvidence log = new EvidenceBundle.LogEvidence();
        log.logFileName = logFile.getName();
        log.candidateTimestampUsed = candidateTimestamp;
        try {
            ServerLogCorrelator.CorrelationResult r = logCorrelator.correlate(logFile.toPath(), candidateTimestamp, 30);
            log.windowExcerpt = mask(limit(r.windowExcerpt, 200));
            log.keywordExcerpt = mask(limit(r.keywordExcerpt, 200));
            log.windowAndKeywordOverlap = r.windowAndKeywordOverlap;
            if (candidateTimestamp != null && !r.windowExcerpt.isEmpty() && !r.windowAndKeywordOverlap) {
                log.note = "덤프 캡처 시각(" + candidateTimestamp + ") 주변 로그와 오류/OOM 키워드 매치 로그가 서로 겹치지 않습니다. "
                        + "로그 파일 자체(" + r.logFirstTimestamp + " ~ " + r.logLastTimestamp + ")와 실제 캡처 시각이 다를 수 있습니다.";
            } else if (candidateTimestamp != null && r.windowExcerpt.isEmpty()) {
                log.note = "덤프 캡처 시각(" + candidateTimestamp + ") 주변에는 로그가 없습니다(로그 범위: "
                        + r.logFirstTimestamp + " ~ " + r.logLastTimestamp + "). 아래 키워드 매치 로그를 참고하되 시각 일치는 확인되지 않았습니다.";
            }
        } catch (Exception e) {
            log.note = "서버 로그 상관관계 분석 중 오류: " + e.getMessage();
        }
        return log;
    }

    private List<String> mask(List<String> lines) {
        List<String> out = new ArrayList<>();
        for (String l : lines) out.add(EvidenceMasker.mask(l));
        return out;
    }

    private List<String> limit(List<String> lines, int max) {
        return lines.size() <= max ? lines : lines.subList(0, max);
    }

    private String buildClaudeInstructions() {
        return "당신은 WAS/힙덤프 분석 보조자입니다. 아래 EVIDENCE(JSON)만 근거로 삼아 한국어 마크다운으로 "
                + "\"결론 및 권고\" 섹션만 작성하세요. 다른 섹션(기본정보/증상/Heap Overview 표/스택)은 이미 별도로 생성되어 있으니 "
                + "다시 만들지 마세요. EVIDENCE에 없는 클래스명·수치·로그·스택은 절대 새로 만들지 마세요. "
                + "여러 덤프나 여러 점유 대상의 수치가 서로 다르면 하나로 통일하지 말고 각 dumpIdentifier/suspectIdentifier를 "
                + "함께 언급하세요. 근거가 부족하면 단정하지 말고 원문 예시처럼 \"~인지 점검 권고\" 같은 가설/권고 톤을 유지하세요. "
                + "출력은 결론 섹션 본문(마크다운)만 반환하고 그 앞뒤에 다른 설명을 붙이지 마세요.";
    }

    private void reportSuccess(ClaimResponse claim, String resultPath) {
        callReport(claim, "SUCCEEDED", null, null, resultPath);
    }

    private void reportFailure(ClaimResponse claim, String failCode, String failReason) {
        callReport(claim, "FAILED", failCode, trim(failReason, 900), null);
    }

    private void callReport(ClaimResponse claim, String newState, String failCode, String failReason, String resultPath) {
        try {
            var result = api.report(claim.jobId, claim.attemptId, config.workerId, claim.jobVersionNo, claim.attemptVersionNo,
                    "RUNNING", newState, failCode, failReason, resultPath);
            if (!result.success) {
                LOGGER.warn("report call was not accepted (stale/conflict?) for attemptId={}: {}", claim.attemptId, result.message);
            }
        } catch (Exception e) {
            LOGGER.error("failed to send final report for attemptId={} (server will move this to RECOVERY_REQUIRED after heartbeat timeout)",
                    claim.attemptId, e);
        }
    }

    private static boolean looksLikeThreadDump(File file) {
        try {
            String head = Files.readString(file.toPath(), java.nio.charset.StandardCharsets.UTF_8);
            return head.contains("Full thread dump") || head.matches("(?s).*\"[^\"]+\"\\s+#\\d+.*prio=.*");
        } catch (Exception e) {
            return false;
        }
    }

    private File pickServerLogCandidate(List<SafeTarGzExtractor.ExtractedFile> files, File extractDir) {
        File best = null;
        for (SafeTarGzExtractor.ExtractedFile f : files) {
            String lower = f.entryName.toLowerCase(Locale.ROOT);
            if (!lower.endsWith(".log") && !lower.endsWith(".txt")) continue;
            if (lower.contains("gc")) continue;
            if (looksLikeThreadDump(f.file)) continue;
            if (best == null || f.file.length() > best.length()) best = f.file;
        }
        return best;
    }

    private static File findTopLevelChart(File dir) {
        File[] children = dir.listFiles((d, name) -> name.toLowerCase(Locale.ROOT).matches("chart\\d+\\.png"));
        return children != null && children.length > 0 ? children[0] : null;
    }

    /** MAT prints "Date: Sep 9, 2026" and "Time: 12:29:59 AM KST" as separate table rows; combine into "yyyy-MM-dd HH:mm:ss". */
    private static String extractTimestamp(java.util.Map<String, String> overviewFields) {
        String date = overviewFields.get("Date");
        String time = overviewFields.get("Time");
        if (date == null || time == null) return null;
        try {
            LocalDate d = LocalDate.parse(date, DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH));
            String timeNoZone = time.replaceAll("\\s*[A-Z]{2,4}$", "").trim();
            LocalTime t = LocalTime.parse(timeNoZone, DateTimeFormatter.ofPattern("h:mm:ss a", Locale.ENGLISH));
            return LocalDateTime.of(d, t).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            return null;
        }
    }

    private static String trim(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static void sleepQuietly(long millis) {
        try { Thread.sleep(millis); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }
}

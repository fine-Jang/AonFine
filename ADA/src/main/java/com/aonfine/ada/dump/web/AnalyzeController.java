package com.aonfine.ada.dump.web;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.aonfine.ada.analysis.AnalysisAttemptVO;
import com.aonfine.ada.analysis.AnalysisResult;
import com.aonfine.ada.analysis.AnalysisService;
import com.aonfine.ada.analysis.AnalysisState;
import com.aonfine.ada.auth.AdaRequestSecurity;
import com.aonfine.ada.auth.AdaUserVO;
import com.aonfine.ada.common.AdaProperties;
import com.aonfine.ada.dump.DumpService;
import com.aonfine.ada.dump.DumpVO;

/**
 * User-facing analysis endpoints. Same session/CSRF rules as DumpUploadController; the internal
 * Worker API (claim/heartbeat/report) lives separately under WorkerAnalysisController with its own
 * token auth. "success" here means the request/status read completed, never that the analysis
 * itself finished -- see AnalysisResult.
 */
@Controller
@RequestMapping(value = "/dump", produces = MediaType.APPLICATION_JSON_VALUE)
public class AnalyzeController {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnalyzeController.class);

    @Resource(name = "analysisService") private AnalysisService analysisService;
    @Resource(name = "dumpService") private DumpService dumpService;
    @Resource private AdaProperties adaProperties;

    @GetMapping("/analysisStatus.do") @ResponseBody
    public AnalysisResult status(@RequestParam("dumpId") String id, HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        AdaUserVO user = requireUser(request, response);
        if (user == null) return AnalysisResult.fail("UNAUTHORIZED", "로그인이 필요합니다.");
        if (!AdaRequestSecurity.validId(id)) { response.setStatus(404); return AnalysisResult.fail("NOT_FOUND", "덤프를 찾을 수 없습니다."); }
        try {
            AnalysisResult result = analysisService.status(id, user.isAdmin(), user.getUserId());
            if (!result.isSuccess()) response.setStatus(404);
            return result;
        } catch (Exception e) {
            response.setStatus(503);
            return AnalysisResult.fail("UNAVAILABLE", "서버 상태를 확인하지 못했습니다.");
        }
    }

    @PostMapping({"/analyzeRequest.do", "/analysisRetry.do"}) @ResponseBody
    public AnalysisResult request(@RequestParam("dumpId") String id,
            @RequestParam(value = "requestKey", required = false) String requestKey,
            HttpServletRequest request, HttpServletResponse response) {
        AnalysisResult mutationError = requireMutation(request, response, id);
        if (mutationError != null) return mutationError;
        AdaUserVO user = AdaRequestSecurity.user(request);
        try {
            AnalysisResult result = analysisService.requestAttempt(id, user.getUserId(), requestKey);
            applyStatus(result, response);
            return result;
        } catch (Exception e) {
            response.setStatus(503);
            return AnalysisResult.fail("UNAVAILABLE", "분석 요청 결과를 확인하지 못했습니다. 상태 조회로 다시 확인해 주세요.");
        }
    }

    @PostMapping("/analysisCancel.do") @ResponseBody
    public AnalysisResult cancel(@RequestParam("dumpId") String id, HttpServletRequest request, HttpServletResponse response) {
        AnalysisResult mutationError = requireMutation(request, response, id);
        if (mutationError != null) return mutationError;
        AdaUserVO user = AdaRequestSecurity.user(request);
        try {
            AnalysisResult result = analysisService.cancel(id, user.isAdmin(), user.getUserId());
            applyStatus(result, response);
            return result;
        } catch (Exception e) {
            response.setStatus(503);
            return AnalysisResult.fail("UNAVAILABLE", "취소 요청 결과를 확인하지 못했습니다. 상태 조회로 다시 확인해 주세요.");
        }
    }

    /**
     * Streams the Worker-produced result package (report .md + images/ + evidence/, zipped).
     * The Worker only ever reports a RELATIVE path; this resolves it under the configured result
     * root and refuses anything that escapes it, so a compromised/buggy Worker cannot make ADA
     * serve arbitrary files.
     */
    @GetMapping("/analyzeResultDownload.do")
    public void resultDownload(@RequestParam("dumpId") String id, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.setHeader("Cache-Control", "no-store");
        AdaUserVO user = AdaRequestSecurity.user(request);
        if (user == null) { response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "로그인이 필요합니다."); return; }
        if (!AdaRequestSecurity.validId(id)) { response.sendError(HttpServletResponse.SC_NOT_FOUND, "덤프를 찾을 수 없습니다."); return; }

        DumpVO dump;
        AnalysisAttemptVO attempt;
        try {
            dump = dumpService.getForAnalysis(id, user.isAdmin(), user.getUserId());
            attempt = analysisService.currentAttemptFor(id, user.isAdmin(), user.getUserId());
        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "서버 상태를 확인하지 못했습니다.");
            return;
        }
        if (dump == null || attempt == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "분석 결과를 찾을 수 없습니다.");
            return;
        }
        if (!AnalysisState.SUCCEEDED.name().equals(attempt.getStatusCd()) || !StringUtils.hasText(attempt.getResultPath())) {
            response.sendError(HttpServletResponse.SC_CONFLICT, "완료된 분석 결과가 없습니다.");
            return;
        }
        if (dump.isExpiredNow()) {
            response.sendError(HttpServletResponse.SC_GONE, "보관 기간이 지나 다운로드할 수 없습니다.");
            return;
        }

        File root = adaProperties.getResultDir();
        File target = new File(root, attempt.getResultPath());
        String rootCanonical = root.getCanonicalPath() + File.separator;
        if (!target.getCanonicalPath().startsWith(rootCanonical)) {
            LOGGER.error("refusing result path outside the result root for dumpId={}", id);
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "분석 결과를 찾을 수 없습니다.");
            return;
        }
        if (!target.isFile()) {
            LOGGER.error("result file missing on disk for dumpId={} attemptId={}", id, attempt.getAttemptId());
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "분석 결과 파일을 찾을 수 없습니다.");
            return;
        }

        String downloadName = downloadName(dump);
        String encoded = URLEncoder.encode(downloadName, "UTF-8").replace("+", "%20");
        response.setContentType("application/zip");
        response.setContentLengthLong(target.length());
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
        Files.copy(target.toPath(), response.getOutputStream());
        response.getOutputStream().flush();
    }

    /** 부처명_업무명_날짜.zip, sanitized; the report file inside the zip keeps the same naming convention. */
    private String downloadName(DumpVO dump) {
        String date = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return sanitize(dump.getDeptNm()) + "_" + sanitize(dump.getTaskNm()) + "_" + date + ".zip";
    }

    private String sanitize(String value) {
        if (value == null || value.trim().isEmpty()) return "미상";
        String cleaned = value.trim().replaceAll("[\\\\/:*?\"<>|\\r\\n\\t]", "_");
        return cleaned.isEmpty() ? "미상" : cleaned;
    }

    private AdaUserVO requireUser(HttpServletRequest request, HttpServletResponse response) {
        AdaUserVO user = AdaRequestSecurity.user(request);
        if (user == null) response.setStatus(401);
        return user;
    }

    private AnalysisResult requireMutation(HttpServletRequest request, HttpServletResponse response, String id) {
        response.setHeader("Cache-Control", "no-store");
        AdaUserVO user = requireUser(request, response);
        if (user == null) return AnalysisResult.fail("UNAUTHORIZED", "로그인이 필요합니다.");
        if (!AdaRequestSecurity.validMutation(request)) {
            response.setStatus(403);
            return AnalysisResult.fail("FORBIDDEN", "요청 인증을 확인해 주세요.");
        }
        if (!AdaRequestSecurity.validId(id)) {
            response.setStatus(404);
            return AnalysisResult.fail("NOT_FOUND", "덤프를 찾을 수 없습니다.");
        }
        return null;
    }

    private void applyStatus(AnalysisResult result, HttpServletResponse response) {
        if (result.isSuccess()) return;
        String code = result.getCode();
        if ("NOT_FOUND".equals(code)) response.setStatus(404);
        else if ("CONFLICT".equals(code) || "ALREADY_TERMINAL".equals(code) || "NO_ACTIVE_ANALYSIS".equals(code)
                || "NOT_READY".equals(code) || "EXPIRED".equals(code)) response.setStatus(409);
        else response.setStatus(400);
    }
}

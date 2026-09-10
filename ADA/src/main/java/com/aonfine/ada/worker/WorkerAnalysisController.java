package com.aonfine.ada.worker;

import java.sql.Timestamp;
import java.util.regex.Pattern;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.aonfine.ada.analysis.AnalysisAttemptVO;
import com.aonfine.ada.analysis.AnalysisJobVO;
import com.aonfine.ada.analysis.AnalysisResult;
import com.aonfine.ada.analysis.AnalysisService;
import com.aonfine.ada.dump.DumpMapper;
import com.aonfine.ada.dump.DumpVO;

/**
 * Internal API for the Worker process only -- guarded by AdaWorkerAuthInterceptor (bearer token +
 * optional IP allowlist), never by the user session/CSRF used under /dump/**. One worker processes
 * one attempt at a time for now: it calls claim.do, then heartbeat.do periodically while it works,
 * then report.do exactly once per attempt (report.do is safe to resend the same completion again).
 */
@Controller
@RequestMapping(value = "/worker/analysis", produces = MediaType.APPLICATION_JSON_VALUE)
public class WorkerAnalysisController {
    private static final Pattern ID = Pattern.compile("[0-9a-f-]{1,64}");
    private static final Pattern WORKER_ID = Pattern.compile("[A-Za-z0-9._-]{1,100}");

    @Resource(name = "analysisService") private AnalysisService analysisService;
    @Resource(name = "dumpMapper") private DumpMapper dumpMapper;

    @PostMapping("/claim.do") @ResponseBody
    public WorkerClaimResult claim(@RequestParam("workerId") String workerId, HttpServletResponse response) {
        if (!WORKER_ID.matcher(workerId).matches()) {
            response.setStatus(400);
            return WorkerClaimResult.fail("workerId 형식이 올바르지 않습니다.");
        }
        AnalysisAttemptVO attempt = analysisService.claimNext(workerId);
        if (attempt == null) return WorkerClaimResult.none();
        AnalysisJobVO job = analysisService.getJob(attempt.getJobId());
        DumpVO dump = job == null ? null : dumpMapper.selectOne(job.getDumpId(), new Timestamp(0));
        return WorkerClaimResult.claimed(attempt, workerId, job == null ? 0 : job.getVersionNo(), dump);
    }

    @PostMapping("/heartbeat.do") @ResponseBody
    public AnalysisResult heartbeat(@RequestParam("attemptId") String attemptId, @RequestParam("workerId") String workerId,
            HttpServletResponse response) {
        if (!validId(attemptId) || !WORKER_ID.matcher(workerId).matches()) {
            response.setStatus(400);
            return AnalysisResult.fail("BAD_REQUEST", "요청 형식이 올바르지 않습니다.");
        }
        boolean ok = analysisService.heartbeat(attemptId, workerId);
        if (!ok) {
            response.setStatus(409);
            return AnalysisResult.fail("STALE", "이 시도는 더 이상 이 worker의 실행 중 상태가 아닙니다. claim 결과를 다시 확인하세요.");
        }
        return AnalysisResult.ok("OK");
    }

    @PostMapping("/report.do") @ResponseBody
    public AnalysisResult report(@RequestParam("jobId") String jobId, @RequestParam("attemptId") String attemptId,
            @RequestParam("workerId") String workerId, @RequestParam("expectedJobVersion") long expectedJobVersion,
            @RequestParam("expectedAttemptVersion") long expectedAttemptVersion,
            @RequestParam("expectedState") String expectedState, @RequestParam("newState") String newState,
            @RequestParam(value = "failCode", required = false) String failCode,
            @RequestParam(value = "failReason", required = false) String failReason,
            @RequestParam(value = "resultPath", required = false) String resultPath,
            HttpServletResponse response) {
        if (!validId(jobId) || !validId(attemptId) || !WORKER_ID.matcher(workerId).matches()) {
            response.setStatus(400);
            return AnalysisResult.fail("BAD_REQUEST", "요청 형식이 올바르지 않습니다.");
        }
        // resultPath must never be an absolute/parent-escaping path: Worker reports a relative path under the attempt's own result root.
        if (resultPath != null && (resultPath.contains("..") || resultPath.startsWith("/") || resultPath.matches("^[A-Za-z]:.*"))) {
            response.setStatus(400);
            return AnalysisResult.fail("BAD_REQUEST", "resultPath는 상대 경로만 허용됩니다.");
        }
        AnalysisResult result = analysisService.report(jobId, attemptId, workerId, expectedJobVersion, expectedAttemptVersion,
                expectedState, newState, failCode, failReason, resultPath);
        if (!result.isSuccess() && "STALE".equals(result.getCode())) response.setStatus(409);
        else if (!result.isSuccess()) response.setStatus(404);
        return result;
    }

    private static boolean validId(String id) { return id != null && ID.matcher(id).matches(); }
}

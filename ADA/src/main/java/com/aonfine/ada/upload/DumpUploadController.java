package com.aonfine.ada.upload;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import com.aonfine.ada.auth.*;
import com.aonfine.ada.common.AdaStorageException;
import com.aonfine.ada.dump.DumpVO;

@Controller
@RequestMapping(value = "/dump", produces = MediaType.APPLICATION_JSON_VALUE)
public class DumpUploadController {
    @Resource(name = "dumpUploadService") private DumpUploadService service;

    @PostMapping("/uploadPrepare.do") @ResponseBody
    public UploadResult prepare(HttpServletRequest request, HttpServletResponse response,
            @RequestParam("dumpType") String type, @RequestParam("deptNm") String dept,
            @RequestParam("taskNm") String task, @RequestParam("hostNm") String host,
            @RequestParam("fileName") String name, @RequestParam("fileSize") long size) {
        if (!authorized(request, response, true)) return UploadResult.fail("로그인 또는 요청 인증을 확인해 주세요.");
        try { return service.prepare(AdaRequestSecurity.user(request).getUserId(), type, dept, task, host, name, size); }
        catch (AdaStorageException e) { response.setStatus(400); return UploadResult.fail(e.getUserMessage()); }
        catch (Exception e) { response.setStatus(503); return UploadResult.fail("업로드 준비 결과를 확인하지 못했습니다. 목록에서 상태를 확인해 주세요."); }
    }

    @PostMapping("/upload.do") @ResponseBody
    public UploadResult upload(HttpServletRequest request, HttpServletResponse response) {
        // Header avoids parameter parsing of a 5GB multipart body before authorization.
        String id = request.getHeader("X-ADA-Dump-ID");
        if (!authorized(request, response, true)) return UploadResult.fail("로그인 또는 요청 인증을 확인해 주세요.");
        try {
            DumpVO vo = accessible(request, response, id);
            if (vo == null) return UploadResult.fail("업로드를 찾을 수 없습니다.");
            // Admin may cancel/view another user's upload, but cannot send a body on their behalf.
            if (!AdaRequestSecurity.user(request).getUserId().equals(vo.getUserId())) {
                response.setStatus(403); return UploadResult.fail("본인의 업로드만 전송할 수 있습니다.");
            }
            return service.handleUpload(request, id);
        } catch (Exception e) { response.setStatus(503); return UploadResult.unknown(id); }
    }

    @PostMapping("/uploadCancel.do") @ResponseBody
    public UploadResult cancel(HttpServletRequest request, HttpServletResponse response, @RequestParam("dumpId") String id) {
        if (!authorized(request, response, true)) return UploadResult.fail("로그인 또는 요청 인증을 확인해 주세요.");
        try {
            if (accessible(request, response, id) == null) return UploadResult.fail("업로드를 찾을 수 없습니다.");
            return service.cancel(id);
        } catch (Exception e) { response.setStatus(503); return UploadResult.unknown(id); }
    }

    @GetMapping("/uploadStatus.do") @ResponseBody
    public UploadResult status(HttpServletRequest request, HttpServletResponse response, @RequestParam("dumpId") String id) {
        response.setHeader("Cache-Control", "no-store");
        if (!authorized(request, response, false)) return UploadResult.fail("로그인이 필요합니다.");
        try {
            DumpVO vo = accessible(request, response, id);
            return vo == null ? UploadResult.fail("업로드를 찾을 수 없습니다.") : UploadResult.state(vo);
        } catch (Exception e) { response.setStatus(503); return UploadResult.unknown(id); }
    }

    private boolean authorized(HttpServletRequest request, HttpServletResponse response, boolean mutation) {
        response.setHeader("Cache-Control", "no-store");
        if (AdaRequestSecurity.user(request) == null) { response.setStatus(401); return false; }
        if (mutation && !AdaRequestSecurity.validMutation(request)) { response.setStatus(403); return false; }
        return true;
    }
    private DumpVO accessible(HttpServletRequest request, HttpServletResponse response, String id) {
        DumpVO vo = AdaRequestSecurity.validId(id) ? service.find(id) : null;
        if (!AdaRequestSecurity.canAccess(AdaRequestSecurity.user(request), vo)) { response.setStatus(404); return null; }
        return vo;
    }
}

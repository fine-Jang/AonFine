package com.aonfine.ada.dump.web;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.file.Files;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.aonfine.ada.auth.AdaSessionConstants;
import com.aonfine.ada.auth.AdaUserVO;
import com.aonfine.ada.common.AdaProperties;
import com.aonfine.ada.common.NfsMountGuard;
import com.aonfine.ada.dump.DumpService;
import com.aonfine.ada.dump.DumpVO;

/**
 * 원본 tar.gz 다운로드. 스트리밍으로 그대로 전송하며 다운로드를 위해 전체를 메모리에 올리지 않는다.
 * 소유권(본인 또는 관리자)과 만료 여부를 항상 서버에서 즉시 재검사한다(정리 배치 주기와 무관).
 */
@Controller
@RequestMapping("/dump")
public class DumpDownloadController {

    private static final Logger LOGGER = LoggerFactory.getLogger(DumpDownloadController.class);

    @Resource(name = "dumpService")
    private DumpService dumpService;

    @Resource
    private AdaProperties adaProperties;

    @Resource
    private NfsMountGuard nfsMountGuard;

    @RequestMapping("/download.do")
    public void download(@RequestParam("dumpId") String dumpId, HttpSession session, HttpServletResponse response)
            throws IOException {
        AdaUserVO user = (AdaUserVO) session.getAttribute(AdaSessionConstants.LOGIN_SESSION_KEY);

        DumpVO vo = dumpService.getForDownload(dumpId, user.isAdmin(), user.getUserId());
        if (vo == null) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "접근 권한이 없습니다.");
            return;
        }
        if (!DumpVO.STATUS_STORED.equals(vo.getStatusCd()) || vo.isExpiredNow()) {
            response.sendError(HttpServletResponse.SC_GONE, "다운로드할 수 없는 파일입니다(만료되었거나 저장되지 않음).");
            return;
        }

        try {
            nfsMountGuard.assertMounted();
        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "스토리지에 접근할 수 없습니다.");
            return;
        }

        File file = new File(adaProperties.getStoreDir(), vo.getStoredFileNm());
        if (!file.exists()) {
            LOGGER.error("dump file missing on disk for dumpId={} path={}", dumpId, file);
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "파일을 찾을 수 없습니다.");
            return;
        }

        String encodedName = URLEncoder.encode(vo.getOriginalFileNm(), "UTF-8").replace("+", "%20");
        response.setContentType("application/octet-stream");
        response.setContentLengthLong(file.length());
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedName);

        Files.copy(file.toPath(), response.getOutputStream());
        response.getOutputStream().flush();
    }
}

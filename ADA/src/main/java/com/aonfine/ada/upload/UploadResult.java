package com.aonfine.ada.upload;

import com.aonfine.ada.dump.DumpVO;
import static com.aonfine.ada.dump.DumpVO.*;

/** success means request/status availability, not upload or cancellation completion. */
public class UploadResult {
    private final boolean success;
    private final String message, dumpId, statusCd;
    private final boolean terminal, retryAllowed;
    private UploadResult(boolean success, String message, String id, String state, boolean terminal, boolean retry) {
        this.success = success; this.message = message; this.dumpId = id; this.statusCd = state;
        this.terminal = terminal; this.retryAllowed = retry;
    }
    public static UploadResult fail(String message) { return new UploadResult(false, message, null, "UNKNOWN", false, false); }
    public static UploadResult unknown(String id) {
        return new UploadResult(false, "서버 상태를 확인하지 못했습니다. 완료로 간주하지 말고 목록에서 다시 확인해 주세요.", id, "UNKNOWN", false, false);
    }
    public static UploadResult state(DumpVO vo) {
        String s = vo.getStatusCd();
        boolean retry = STATUS_CANCELLED.equals(s) || STATUS_FAILED.equals(s);
        String message = vo.getFailReason();
        if (message == null || message.isEmpty()) message = vo.getStatusLabel();
        if (STATUS_CANCEL_REQUESTED.equals(s)) message = "취소 요청을 접수했습니다. 서버 처리 중단과 파일 정리를 확인 중입니다.";
        if (STATUS_FINALIZING.equals(s)) message = "저장 확정 중에는 취소할 수 없습니다. 최종 상태를 확인해 주세요.";
        if (retry) message += " 파일을 다시 선택하여 재업로드할 수 있습니다.";
        return new UploadResult(true, message, vo.getDumpId(), s,
                retry || STATUS_STORED.equals(s) || STATUS_EXPIRED.equals(s), retry);
    }
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public String getDumpId() { return dumpId; }
    public String getStatusCd() { return statusCd; }
    public boolean isTerminal() { return terminal; }
    public boolean isRetryAllowed() { return retryAllowed; }
}

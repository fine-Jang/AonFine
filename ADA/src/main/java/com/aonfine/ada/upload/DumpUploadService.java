package com.aonfine.ada.upload;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Timestamp;
import java.util.UUID;
import java.util.concurrent.*;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.ServletInputStream;
import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.aonfine.ada.common.*;
import com.aonfine.ada.dump.*;
import static com.aonfine.ada.dump.DumpVO.*;

/** One WAS upload owner. DB compare-and-set fences duplicate bodies and late cancellation. */
@Service("dumpUploadService")
public class DumpUploadService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DumpUploadService.class);
    @Resource private AdaProperties adaProperties;
    @Resource private NfsMountGuard nfsMountGuard;
    @Resource private StorageSpaceChecker storageSpaceChecker;
    @Resource(name = "dumpMapper") private DumpMapper dumpMapper;
    private TarGzStreamValidator validator;
    private final ConcurrentMap<String, Control> active = new ConcurrentHashMap<>();
    // A stalled container close must not block the endpoint or create unlimited threads.
    private final ThreadPoolExecutor closers = new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<Runnable>(32), r -> {
                Thread t = new Thread(r, "ada-upload-stream-close"); t.setDaemon(true); return t;
            }, new ThreadPoolExecutor.AbortPolicy());

    @PostConstruct public void init() { validator = new TarGzStreamValidator(adaProperties); }
    @PreDestroy public void stop() {
        active.values().forEach(c -> { c.cancelled = true; c.closeInput(); });
        closers.shutdown();
    }

    /** Serializes check+insert on WAS2. Not a multi-WAS admission lock. */
    public synchronized UploadResult prepare(String userId, String type, String dept, String task,
            String host, String name, long size) throws AdaStorageException {
        if (!isValidDumpType(type)) throw new AdaStorageException("덤프 유형을 선택해 주세요.");
        requireText(dept, 200, "부처명"); requireText(task, 200, "업무명"); requireText(host, 200, "호스트명");
        requireText(name, 500, "파일명");
        if (!name.toLowerCase(java.util.Locale.ROOT).endsWith(".tar.gz"))
            throw new AdaStorageException("tar.gz 파일을 선택해 주세요.");
        if (size <= 0 || size > adaProperties.getMaxFileSizeBytes())
            throw new AdaStorageException("비어 있지 않은 5GB 미만 파일을 선택해 주세요.");
        if (dumpMapper.countActiveUploadsByUser(userId) > 0)
            throw new AdaStorageException("진행 중이거나 정리 확인이 필요한 업로드가 있습니다. 목록에서 상태를 확인해 주세요.");
        nfsMountGuard.assertMounted();
        try {
            Files.createDirectories(adaProperties.getStagingDir().toPath());
            Files.createDirectories(adaProperties.getStoreDir().toPath());
        } catch (IOException e) { throw new AdaStorageException("업로드 저장 경로를 준비하지 못했습니다. 관리자에게 문의해 주세요.", e); }
        storageSpaceChecker.assertHasSpace(size);
        DumpVO vo = new DumpVO();
        vo.setDumpId(UUID.randomUUID().toString()); vo.setUserId(userId); vo.setDumpType(type);
        vo.setDeptNm(dept.trim()); vo.setTaskNm(task.trim()); vo.setHostNm(host.trim());
        vo.setOriginalFileNm(name); vo.setStoredFileNm(vo.getDumpId() + ".tar.gz"); vo.setFileSize(size);
        dumpMapper.insertUploading(vo);
        return status(vo.getDumpId());
    }

    private void requireText(String value, int max, String label) throws AdaStorageException {
        if (value == null || value.trim().isEmpty() || value.length() > max)
            throw new AdaStorageException(label + "은(는) 1~" + max + "자여야 합니다.");
    }

    public DumpVO find(String id) { return dumpMapper.selectOne(id, new Timestamp(0)); }
    public UploadResult status(String id) {
        DumpVO vo = find(id);
        return vo == null ? UploadResult.fail("업로드를 찾을 수 없습니다.") : UploadResult.state(vo);
    }

    public UploadResult cancel(String id) {
        DumpVO vo = find(id);
        if (vo == null) return UploadResult.fail("업로드를 찾을 수 없습니다.");
        String previous = vo.getStatusCd();
        if (STATUS_READY.equals(previous) || STATUS_UPLOADING.equals(previous) || STATUS_VALIDATING.equals(previous)) {
            if (dumpMapper.transition(id, previous, STATUS_CANCEL_REQUESTED, null) == 1) {
                if (STATUS_READY.equals(previous)) {
                    // Claim requires READY: no body handler can create files after this winning CAS.
                    finishFailure(id, true, "사용자가 업로드를 취소했습니다.");
                }
            } else return cancelCurrent(id);
        }
        signalCancellation(id);
        return status(id);
    }

    private UploadResult cancelCurrent(String id) {
        for (int i = 0; i < 3; i++) {
            DumpVO vo = find(id);
            if (vo == null) return UploadResult.fail("업로드를 찾을 수 없습니다.");
            String state = vo.getStatusCd();
            if (!STATUS_UPLOADING.equals(state) && !STATUS_VALIDATING.equals(state)) break;
            if (dumpMapper.transition(id, state, STATUS_CANCEL_REQUESTED, null) == 1) break;
        }
        signalCancellation(id);
        return status(id);
    }

    private void signalCancellation(String id) {
        DumpVO vo = find(id);
        if (vo != null && STATUS_CANCEL_REQUESTED.equals(vo.getStatusCd())) {
            Control control = active.get(id);
            if (control != null) { control.cancelled = true; control.closeInput(); }
        }
    }

    public UploadResult handleUpload(HttpServletRequest request, String id) {
        Control control = new Control(id);
        // Only the CAS winner may own/clean these paths. Duplicate bodies never touch files.
        if (dumpMapper.transition(id, STATUS_READY, STATUS_UPLOADING, null) != 1) return status(id);
        active.put(id, control);
        try {
            DumpVO vo = find(id);
            nfsMountGuard.assertMounted();
            Files.createDirectories(adaProperties.getStagingDir().toPath());
            Files.createDirectories(adaProperties.getStoreDir().toPath());
            if (!ServletFileUpload.isMultipartContent(request)) throw new AdaStorageException("올바른 파일 업로드 요청이 아닙니다.");
            long bytes = 0;
            // Close the raw request, not FileItemStream.close() (which can drain remaining multipart bytes).
            try (ServletInputStream raw = request.getInputStream()) {
                control.input = raw;
                control.check();
                HttpServletRequest wrapped = new HttpServletRequestWrapper(request) {
                    @Override public ServletInputStream getInputStream() { return raw; }
                };
                ServletFileUpload upload = new ServletFileUpload();
                upload.setFileSizeMax(adaProperties.getMaxFileSizeBytes());
                upload.setSizeMax(adaProperties.getMaxFileSizeBytes() + 65536L);
                FileItemIterator iter = upload.getItemIterator(wrapped);
                boolean seen = false;
                while (iter.hasNext()) {
                    control.check();
                    FileItemStream item = iter.next();
                    if (seen || item.isFormField() || !"dumpFile".equals(item.getFieldName()))
                        throw new AdaStorageException("파일은 dumpFile 항목으로 1개만 전송해 주세요.");
                    seen = true;
                    bytes = streamToPartFile(item.openStream(), partFile(id), control);
                }
                if (!seen || bytes != vo.getFileSize()) throw new AdaStorageException("전송된 파일 크기가 일치하지 않습니다. 파일을 다시 선택해 주세요.");
            } finally { control.input = null; }
            control.check();
            if (dumpMapper.transition(id, STATUS_UPLOADING, STATUS_VALIDATING, null) != 1) throw new UploadCancellation.Cancelled();
            validator.validate(partFile(id), vo.getDumpType(), control);
            control.check();
            nfsMountGuard.assertMounted();
            // Linearization point: cancellation before this wins; after this cannot delete the stored file.
            if (dumpMapper.transition(id, STATUS_VALIDATING, STATUS_FINALIZING, null) != 1) throw new UploadCancellation.Cancelled();
            Files.move(partFile(id).toPath(), finalFile(id).toPath(), StandardCopyOption.ATOMIC_MOVE);
            if (dumpMapper.markStored(id, vo.getOriginalFileNm(), vo.getStoredFileNm(), bytes) != 1)
                throw new IllegalStateException("store transition rejected");
            return status(id);
        } catch (Exception e) {
            LOGGER.warn("Upload processing stopped: dumpId={}, type={}", id, e.getClass().getSimpleName());
            String reason = e instanceof AdaStorageException ? ((AdaStorageException) e).getUserMessage()
                    : (e instanceof org.apache.commons.fileupload.FileUploadBase.FileSizeLimitExceededException
                        || e instanceof org.apache.commons.fileupload.FileUploadBase.SizeLimitExceededException)
                    ? "업로드 크기 한도를 초과했습니다. 5GB 미만 파일을 선택해 주세요."
                    : e instanceof IOException ? "파일 전송 또는 저장소 입출력에 실패했습니다."
                    : "서버 처리 또는 DB 상태 저장에 실패했습니다. 관리자에게 문의해 주세요.";
            try {
                return finishFailure(id, control.cancelled || e instanceof UploadCancellation.Cancelled, reason);
            } catch (Exception unavailable) {
                // A commit may have succeeded even if its response was lost. Never delete without reading/fencing DB state.
                LOGGER.warn("Upload state requires reconciliation: dumpId={}", id);
                return UploadResult.unknown(id);
            }
        } finally { active.remove(id, control); }
    }

    private long streamToPartFile(InputStream in, File part, UploadCancellation control) throws Exception {
        long total = 0, nextCheck = adaProperties.getSpaceCheckIntervalBytes();
        byte[] buf = new byte[256 * 1024];
        try (OutputStream out = new FileOutputStream(part)) {
            while (true) {
                control.check();
                int read = in.read(buf);
                control.check();
                if (read == -1) break;
                total += read;
                if (total > adaProperties.getMaxFileSizeBytes()) throw new AdaStorageException("업로드 가능한 파일 크기는 5GB 미만입니다.");
                if (total >= nextCheck) {
                    nfsMountGuard.assertMounted(); storageSpaceChecker.assertHasSpace(0);
                    nextCheck = total + adaProperties.getSpaceCheckIntervalBytes();
                }
                out.write(buf, 0, read);
            }
        }
        return total;
    }

    /** Only the body owner after stream scopes exit, or the winning READY cancellation, may call this. */
    private UploadResult finishFailure(String id, boolean cancelled, String reason) {
        DumpVO vo = find(id);
        if (vo == null) return UploadResult.unknown(id);
        String previous = vo.getStatusCd();
        if (STATUS_STORED.equals(previous) || STATUS_CANCELLED.equals(previous) || STATUS_FAILED.equals(previous)
                || STATUS_EXPIRED.equals(previous)) return UploadResult.state(vo);
        cancelled = cancelled || STATUS_CANCEL_REQUESTED.equals(previous);
        if (!(STATUS_UPLOADING.equals(previous) || STATUS_VALIDATING.equals(previous)
                || STATUS_CANCEL_REQUESTED.equals(previous) || STATUS_FINALIZING.equals(previous)))
            return UploadResult.state(vo);
        if (dumpMapper.transition(id, previous, STATUS_CLEANUP_PENDING, reason) != 1)
            return finishFailure(id, cancelled, reason);
        try {
            nfsMountGuard.assertMounted();
            deleteOwned(partFile(id), adaProperties.getStagingDir());
            deleteOwned(finalFile(id), adaProperties.getStoreDir());
        } catch (Exception cleanup) {
            dumpMapper.transition(id, STATUS_CLEANUP_PENDING, STATUS_CLEANUP_FAILED,
                    "파일 정리를 확인하지 못했습니다. 취소 완료가 아닙니다. 관리자 확인이 필요합니다.");
            return status(id);
        }
        dumpMapper.transition(id, STATUS_CLEANUP_PENDING, cancelled ? STATUS_CANCELLED : STATUS_FAILED,
                cancelled ? "업로드가 취소되었습니다. 서버 처리 중단과 파일 정리를 확인했습니다." : reason);
        return status(id);
    }

    private void deleteOwned(File file, File root) throws IOException {
        if (!file.getCanonicalPath().startsWith(root.getCanonicalPath() + File.separator)) throw new IOException("unsafe path");
        Files.deleteIfExists(file.toPath());
        if (Files.exists(file.toPath())) throw new IOException("cleanup not confirmed");
    }
    private File partFile(String id) { return new File(adaProperties.getStagingDir(), id + ".tar.gz.part"); }
    private File finalFile(String id) { return new File(adaProperties.getStoreDir(), id + ".tar.gz"); }

    private class Control implements UploadCancellation {
        final String id;
        volatile boolean cancelled;
        volatile InputStream input;
        final java.util.concurrent.atomic.AtomicBoolean closeScheduled = new java.util.concurrent.atomic.AtomicBoolean();
        long lastCheck;
        Control(String id) { this.id = id; }
        @Override public void check() throws AdaStorageException {
            if (cancelled) throw new UploadCancellation.Cancelled();
            long now = System.nanoTime();
            if (lastCheck == 0 || now - lastCheck >= TimeUnit.MILLISECONDS.toNanos(250)) {
                lastCheck = now;
                DumpVO vo = find(id);
                if (vo == null || STATUS_CANCEL_REQUESTED.equals(vo.getStatusCd())) {
                    cancelled = true; throw new UploadCancellation.Cancelled();
                }
                if (!STATUS_UPLOADING.equals(vo.getStatusCd()) && !STATUS_VALIDATING.equals(vo.getStatusCd()))
                    throw new AdaStorageException("업로드 상태가 변경되어 처리를 중단했습니다.");
            }
        }
        void closeInput() {
            InputStream stream = input;
            if (stream == null || !closeScheduled.compareAndSet(false, true)) return;
            try { closers.execute(() -> { try { stream.close(); } catch (IOException ignored) { /* owner confirms cleanup */ } }); }
            catch (RejectedExecutionException busy) { closeScheduled.set(false); /* allow later retry; no completion claim */ }
        }
    }
}

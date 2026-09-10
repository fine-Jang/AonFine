package com.aonfine.ada.upload;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aonfine.ada.common.AdaProperties;
import com.aonfine.ada.common.AdaStorageException;
import com.aonfine.ada.common.PathSafetyUtil;
import com.aonfine.ada.dump.DumpVO;

/**
 * 이미 디스크에 저장된 .part 파일을 대상으로 gzip/tar 구조와 손상 여부, 그리고 덤프 유형별
 * 실제 내용까지 검증한다. 어떤 엔트리도 디스크에 실제로 풀지 않고(스트림만 순차적으로 읽어
 * 버림) "순차 검증" 방식으로 확인한다.
 *
 * 공통 확인 항목:
 *  - gzip 헤더/트레일러(CRC) 손상 여부, tar 구조 자체의 손상 여부
 *  - 절대경로/상위 디렉터리 이동/특수 파일(symlink, link, fifo, device) 거절
 *  - 엔트리 개수/총 해제 용량/해제 비율/검증 소요 시간 상한 초과 여부 (zip bomb 방어)
 *
 * 덤프 유형별 확인 항목:
 *  - HEAP: 확장자는 hprof/log/txt만 허용. *.hprof로 이름 붙은 모든 엔트리는
 *    {@link HprofFramingChecker}로 헤더 매직, 최상위 레코드 길이/잘림, 종료 구조까지 검증하며
 *    하나라도 구조가 잘못되면 전체 업로드를 거절한다(확장자만 hprof인 가짜 파일 차단).
 *    최소 1개의 유효한 hprof 엔트리가 있어야 한다.
 *  - THREAD: 확장자는 log/txt만 허용(hprof 불허). 그 중 최소 1개는
 *    {@link ThreadDumpSignatureChecker}로 실제 HotSpot 계열 스레드 덤프 시그니처를 포함해야
 *    한다(다른 txt/log는 부가자료로 허용, 개별 검증하지 않음).
 *
 * 이 검증을 통과했다고 해서 실제 분석(이후 단계)이 성공한다고 보장하지 않는다 - 여기서는
 * 파일이 해당 유형의 최소 구조적 요건을 만족하는지만 확인한다.
 */
public class TarGzStreamValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(TarGzStreamValidator.class);

    private final AdaProperties adaProperties;

    public TarGzStreamValidator(AdaProperties adaProperties) {
        this.adaProperties = adaProperties;
    }

    public static final class Result {
        public boolean hprofFound;
        public boolean threadDumpFound;
        public int entryCount;
        public long totalDecompressedBytes;
    }

    public Result validate(File tarGzFile, String dumpType) throws AdaStorageException {
        return validate(tarGzFile, dumpType, UploadCancellation.NONE);
    }

    public Result validate(File tarGzFile, String dumpType, UploadCancellation cancellation) throws AdaStorageException {
        cancellation.check();
        boolean isHeap = DumpVO.DUMP_TYPE_HEAP.equals(dumpType);
        boolean isThread = DumpVO.DUMP_TYPE_THREAD.equals(dumpType);
        if (!isHeap && !isThread) {
            throw new AdaStorageException("알 수 없는 덤프 유형입니다: " + dumpType);
        }

        long startedAt = System.currentTimeMillis();
        long maxValidationMillis = adaProperties.getMaxValidationSeconds() * 1000L;
        long compressedSize = tarGzFile.length();
        Result result = new Result();
        ThreadDumpSignatureChecker threadChecker = new ThreadDumpSignatureChecker();

        byte[] buf = new byte[64 * 1024];

        try (InputStream fileIn = new BufferedInputStream(new FileInputStream(tarGzFile), 256 * 1024);
                GZIPInputStream gzipIn = new GZIPInputStream(fileIn, 64 * 1024);
                InputStream guarded = new GuardedStream(gzipIn, cancellation, compressedSize, startedAt);
                TarArchiveInputStream tarIn = new TarArchiveInputStream(guarded)) {

            TarArchiveEntry entry;
            while ((entry = nextEntrySafely(tarIn)) != null) {
                result.entryCount++;

                if (result.entryCount > adaProperties.getMaxTarEntries()) {
                    throw new AdaStorageException(
                            "압축 파일 안의 항목 수가 허용 한도를 초과했습니다. 파일이 손상되었거나 위험한 파일일 수 있습니다.");
                }
                if (System.currentTimeMillis() - startedAt > maxValidationMillis) {
                    throw new AdaStorageException("압축 파일 검증 시간이 초과되었습니다. 관리자에게 문의해 주세요.");
                }

                String name = entry.getName();
                if (PathSafetyUtil.isDangerousEntryName(name)) {
                    throw new AdaStorageException("압축 파일 안에 허용되지 않는 경로가 포함되어 있습니다: " + name);
                }
                if (entry.isSymbolicLink() || entry.isLink()
                        || (entry.getFile() != null && !entry.isFile() && !entry.isDirectory())) {
                    throw new AdaStorageException("압축 파일 안에 허용되지 않는 특수 파일이 포함되어 있습니다: " + name);
                }

                if (entry.isDirectory()) {
                    continue;
                }

                String lowerName = name.toLowerCase(java.util.Locale.ROOT);
                boolean isHprofName = lowerName.endsWith(".hprof");
                boolean isLog = lowerName.endsWith(".log");
                boolean isTxt = lowerName.endsWith(".txt");

                boolean extensionAllowed = isHeap ? (isHprofName || isLog || isTxt) : (isLog || isTxt);
                if (!extensionAllowed) {
                    String allowed = isHeap ? "hprof, log, txt" : "log, txt";
                    throw new AdaStorageException(
                            "지원하지 않는 파일 형식이 포함되어 있습니다: " + name + " (" + allowed + "만 허용)");
                }

                HprofFramingChecker hprofChecker = (isHeap && isHprofName) ? new HprofFramingChecker() : null;
                boolean feedThreadChecker = isThread && (isLog || isTxt);

                long entryDecompressed = 0L;
                int read;
                while ((read = tarIn.read(buf)) != -1) {
                    entryDecompressed += read;
                    result.totalDecompressedBytes += read;

                    if (result.totalDecompressedBytes > adaProperties.getMaxDecompressedTotalBytes()) {
                        throw new AdaStorageException(
                                "압축 해제 시 총 용량이 허용 한도를 초과합니다. 파일이 손상되었거나 위험한 파일일 수 있습니다.");
                    }
                    long ratio = compressedSize > 0 ? result.totalDecompressedBytes / compressedSize : 0;
                    if (ratio > adaProperties.getMaxCompressionRatio()) {
                        throw new AdaStorageException("압축률이 비정상적으로 높습니다(폭탄 압축 의심). 업로드가 거절되었습니다.");
                    }
                    if (System.currentTimeMillis() - startedAt > maxValidationMillis) {
                        throw new AdaStorageException("압축 파일 검증 시간이 초과되었습니다. 관리자에게 문의해 주세요.");
                    }

                    if (hprofChecker != null) {
                        hprofChecker.accept(buf, 0, read);
                    }
                    if (feedThreadChecker) {
                        threadChecker.accept(buf, 0, read);
                    }
                }

                if (hprofChecker != null) {
                    hprofChecker.finish();
                    if (hprofChecker.isInvalid()) {
                        throw new AdaStorageException(
                                "hprof 파일(" + name + ")의 내용이 유효하지 않습니다: " + hprofChecker.getFailReason());
                    }
                    result.hprofFound = true;
                }

                LOGGER.debug("validated entry {} ({} bytes)", name, entryDecompressed);
            }

            drainRemaining(guarded, buf);

        } catch (AdaStorageException e) {
            throw e;
        } catch (IOException e) {
            if (e.getCause() instanceof AdaStorageException) throw (AdaStorageException) e.getCause();
            LOGGER.warn("dump archive validation failed: {}", e.getMessage());
            throw new AdaStorageException("압축 파일이 손상되었거나 올바른 tar.gz 형식이 아닙니다.", e);
        }

        result.threadDumpFound = threadChecker.isFound();

        if (isHeap && !result.hprofFound) {
            throw new AdaStorageException("hprof 파일이 최소 1개 포함되어야 합니다.");
        }
        if (isThread && !result.threadDumpFound) {
            throw new AdaStorageException(
                    "실제 스레드 덤프로 인식되는 파일이 없습니다 (HotSpot 계열 jstack/kill -3 형식의 txt 또는 log 파일이 최소 1개 필요합니다).");
        }
        return result;
    }

    private TarArchiveEntry nextEntrySafely(TarArchiveInputStream tarIn) throws AdaStorageException {
        try {
            return tarIn.getNextTarEntry();
        } catch (IOException e) {
            if (e.getCause() instanceof AdaStorageException) throw (AdaStorageException) e.getCause();
            throw new AdaStorageException("압축 파일이 손상되었거나 올바른 tar.gz 형식이 아닙니다.", e);
        }
    }

    private void drainRemaining(InputStream in, byte[] buf) throws IOException {
        while (in.read(buf) != -1) {
            // gzip 트레일러(CRC32/ISIZE) 검증을 위해 끝까지 읽어서 버린다.
        }
    }

    /** Includes tar metadata, skipped bytes and gzip trailer drain in cancellation/time/size limits. */
    private class GuardedStream extends java.io.FilterInputStream {
        private final UploadCancellation cancellation;
        private final long compressed, started;
        private long total;
        GuardedStream(InputStream in, UploadCancellation cancellation, long compressed, long started) {
            super(in); this.cancellation = cancellation; this.compressed = compressed; this.started = started;
        }
        private void check(int count) throws IOException {
            if (count > 0) total += count;
            try {
                cancellation.check();
                if (System.currentTimeMillis() - started > adaProperties.getMaxValidationSeconds() * 1000L)
                    throw new AdaStorageException("압축 파일 검증 시간이 초과되었습니다.");
                if (total > adaProperties.getMaxDecompressedTotalBytes()
                        || (compressed > 0 && total / compressed > adaProperties.getMaxCompressionRatio()))
                    throw new AdaStorageException("압축 해제 총 용량 또는 압축률 한도를 초과했습니다.");
            } catch (AdaStorageException e) { throw new IOException(e); }
        }
        @Override public int read() throws IOException {
            check(0); int value = in.read(); check(value < 0 ? 0 : 1); return value;
        }
        @Override public int read(byte[] b, int off, int len) throws IOException {
            check(0); int n = in.read(b, off, Math.min(len, 65536)); check(n); return n;
        }
        @Override public long skip(long n) throws IOException {
            byte[] b = new byte[(int) Math.min(65536, Math.max(1, n))];
            long skipped = 0;
            while (skipped < n) { int r = read(b, 0, (int) Math.min(b.length, n - skipped)); if (r < 0) break; skipped += r; }
            return skipped;
        }
    }
}

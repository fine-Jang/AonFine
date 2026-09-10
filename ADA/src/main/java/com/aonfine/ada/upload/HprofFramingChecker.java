package com.aonfine.ada.upload;

import java.nio.charset.StandardCharsets;

/**
 * HPROF(Java heap dump) 파일의 구조를 스트리밍으로 검사한다. 힙 오브젝트 그래프의 의미적
 * 정합성(참조 관계 등)까지는 검증하지 않으며, 아래 범위만 확인한다:
 *
 *  1. 헤더 매직 문자열이 "JAVA PROFILE "로 시작하는지 (HotSpot/OpenJDK가 만드는 표준 HPROF 헤더)
 *  2. NUL로 끝나는 버전 문자열, 4바이트 identifier size, 8바이트 타임스탬프로 이어지는 고정 헤더 구조
 *  3. 그 뒤로 이어지는 최상위 레코드들(1바이트 태그 + 4바이트 시간차 + 4바이트 길이 + 데이터)을
 *     끝까지 순차적으로 따라가며, 각 레코드가 선언한 길이가 실제 남은 데이터 범위 안에 있는지 확인
 *  4. 파일이 레코드 헤더 도중이나 데이터 도중에 끊기지 않고, 레코드 경계에서 깔끔하게 끝나는지
 *     ("필수 종료 구조") 확인 - 이걸로 잘림(truncation)과 0바이트에 가까운 가짜 파일을 잡아낸다
 *
 * 실제로 어떤 바이트도 별도로 버퍼링해 보관하지 않고, 청크가 들어오는 대로 상태만 전진시키므로
 * 파일 크기와 무관하게 메모리 사용량이 일정하다(스트리밍).
 */
class HprofFramingChecker {

    private static final byte[] MAGIC_PREFIX = "JAVA PROFILE ".getBytes(StandardCharsets.US_ASCII);

    private enum State {
        MAGIC, VERSION_STRING, ID_SIZE, TIMESTAMP, RECORD_TAG, RECORD_TIME, RECORD_LEN, RECORD_SKIP, DONE
    }

    private State state = State.MAGIC;
    private long totalBytesSeen = 0;
    private int magicMatched = 0;
    private long fieldRemaining = 4; // ID_SIZE 필드부터는 고정 길이 필드를 채우는 데 쓰는 카운터
    private final byte[] fieldBuf = new byte[8];
    private int fieldPos = 0;
    private long currentRecordLen = 0;
    private long recordsSeen = 0;

    private boolean invalid = false;
    private String failReason = null;

    void accept(byte[] buf, int off, int len) {
        if (invalid || state == State.DONE) {
            return;
        }
        for (int i = 0; i < len && !invalid; i++) {
            byte b = buf[off + i];
            totalBytesSeen++;
            switch (state) {
                case MAGIC:
                    if (magicMatched < MAGIC_PREFIX.length) {
                        if (b != MAGIC_PREFIX[magicMatched]) {
                            fail("HPROF 헤더 매직 문자열이 올바르지 않습니다 (진짜 힙덤프가 아닌 것으로 보입니다).");
                            return;
                        }
                        magicMatched++;
                        if (magicMatched == MAGIC_PREFIX.length) {
                            state = State.VERSION_STRING;
                        }
                    }
                    break;
                case VERSION_STRING:
                    if (b == 0) {
                        state = State.ID_SIZE;
                        fieldPos = 0;
                    }
                    // NUL 전까지는 버전 번호 문자열(예: "1.0.1") - 값 자체는 검증하지 않는다.
                    break;
                case ID_SIZE:
                    fieldBuf[fieldPos++] = b;
                    if (fieldPos == 4) {
                        state = State.TIMESTAMP;
                        fieldPos = 0;
                    }
                    break;
                case TIMESTAMP:
                    fieldPos++;
                    if (fieldPos == 8) {
                        state = State.RECORD_TAG;
                        fieldPos = 0;
                    }
                    break;
                case RECORD_TAG:
                    // 태그 값 자체는 벤더/버전마다 확장될 수 있어 화이트리스트로 제한하지 않고,
                    // 뒤따르는 시간차/길이 필드가 실제 데이터 범위 안에 있는지로 구조를 검증한다.
                    state = State.RECORD_TIME;
                    fieldPos = 0;
                    break;
                case RECORD_TIME:
                    fieldPos++;
                    if (fieldPos == 4) {
                        state = State.RECORD_LEN;
                        fieldPos = 0;
                        currentRecordLen = 0;
                    }
                    break;
                case RECORD_LEN:
                    currentRecordLen = (currentRecordLen << 8) | (b & 0xFF);
                    fieldPos++;
                    if (fieldPos == 4) {
                        if (currentRecordLen < 0) {
                            fail("HPROF 레코드 길이 값이 올바르지 않습니다.");
                            return;
                        }
                        recordsSeen++;
                        if (currentRecordLen == 0) {
                            state = State.RECORD_TAG;
                        } else {
                            state = State.RECORD_SKIP;
                            fieldRemaining = currentRecordLen;
                        }
                    }
                    break;
                case RECORD_SKIP:
                    fieldRemaining--;
                    if (fieldRemaining == 0) {
                        state = State.RECORD_TAG;
                    }
                    break;
                default:
                    break;
            }
        }
    }

    /** 엔트리 스트림 끝(EOF)에 도달했을 때 호출해 "필수 종료 구조"가 만족됐는지 최종 판정한다. */
    void finish() {
        if (invalid) {
            return;
        }
        if (magicMatched < MAGIC_PREFIX.length) {
            fail("HPROF 파일이 너무 짧아 헤더를 확인할 수 없습니다.");
            return;
        }
        if (state != State.RECORD_TAG) {
            // 레코드 헤더나 데이터 도중에 파일이 끝났다 = 잘림(truncation)
            fail("HPROF 파일이 잘려있거나 손상되어 정상적으로 끝나지 않습니다.");
            return;
        }
        if (recordsSeen == 0) {
            fail("HPROF 파일에 유효한 레코드가 하나도 없습니다.");
        }
    }

    private void fail(String reason) {
        invalid = true;
        failReason = reason;
    }

    boolean isInvalid() {
        return invalid;
    }

    String getFailReason() {
        return failReason;
    }
}

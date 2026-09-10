package com.aonfine.ada.common;

/**
 * 업로드/검증/저장 과정에서 사용자에게 그대로 보여줄 수 있는 원인 메시지를 담는 예외.
 * userMessage는 화면에 노출되는 문구, 내부 로그는 별도로 상세히 남긴다.
 */
public class AdaStorageException extends Exception {
    private static final long serialVersionUID = 1L;

    private final String userMessage;

    public AdaStorageException(String userMessage) {
        super(userMessage);
        this.userMessage = userMessage;
    }

    public AdaStorageException(String userMessage, Throwable cause) {
        super(userMessage, cause);
        this.userMessage = userMessage;
    }

    public String getUserMessage() {
        return userMessage;
    }
}

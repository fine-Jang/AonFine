package com.aonfine.ada.upload;

import com.aonfine.ada.common.AdaStorageException;

/** Checked at every stream boundary, including archive padding/trailer reads. */
@FunctionalInterface
public interface UploadCancellation {
    UploadCancellation NONE = () -> { };
    void check() throws AdaStorageException;

    class Cancelled extends AdaStorageException {
        private static final long serialVersionUID = 1L;
        public Cancelled() { super("업로드 취소가 요청되었습니다."); }
    }
}

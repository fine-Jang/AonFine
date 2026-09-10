package com.aonfine.ada.common;

import java.io.File;

import javax.annotation.Resource;

import org.springframework.stereotype.Component;

/**
 * 업로드 시작 전과 스트리밍 저장 도중 주기적으로 남은 디스크 공간을 확인한다.
 * 확인 대상은 항상 실제 저장 디렉터리(NFS 마운트 지점 하위)이므로, 마운트가 풀려 로컬 디스크가
 * 보이는 상황이면 로컬 디스크의 여유공간이 조회되어 결과가 왜곡될 수 있다 - 그래서 이 클래스는
 * 반드시 {@link NfsMountGuard}로 먼저 마운트를 확인한 다음에만 호출해야 한다.
 */
@Component
public class StorageSpaceChecker {

    @Resource
    private AdaProperties adaProperties;

    /**
     * @param expectedAdditionalBytes 앞으로 더 받을 것으로 예상되는 바이트 수 (업로드 시작 시점엔 Content-Length,
     *                                 저장 도중엔 남은 예상치를 넉넉히 잡아 0을 넘겨도 됨)
     */
    public void assertHasSpace(long expectedAdditionalBytes) throws AdaStorageException {
        File dir = adaProperties.getStagingDir();
        long usable = dir.getUsableSpace();
        long required = expectedAdditionalBytes + adaProperties.getMinFreeSpaceReserveBytes();
        if (usable < required) {
            throw new AdaStorageException("스토리지 공간이 부족하여 업로드할 수 없습니다.");
        }
    }
}

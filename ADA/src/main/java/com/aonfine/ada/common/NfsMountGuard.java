package com.aonfine.ada.common;

import java.io.File;

import javax.annotation.Resource;

import org.springframework.stereotype.Component;

/**
 * NFS 미마운트 시 로컬 /DATA로 잘못 저장되는 것을 막기 위한 확인 로직.
 *
 * 1차 방어선: Worker에서 NFS 구성 시 WAS2 로컬 /DATA를 root:root 700으로 잠가두었으므로
 *            jboss 프로세스로 실행되는 이 애플리케이션은 마운트가 풀리면 애초에 그 디렉터리에
 *            쓰기 권한이 없어 IOException이 난다.
 * 2차 방어선(이 클래스): Worker의 실제 NFS export 안에 미리 만들어 둔 마커 파일이
 *            보이는지 확인한다. 마운트가 풀리면 이 파일도 당연히 사라지므로, 권한 오류를
 *            기다리지 않고 명확한 사용자 메시지로 먼저 차단할 수 있다.
 */
@Component
public class NfsMountGuard {

    @Resource
    private AdaProperties adaProperties;

    public void assertMounted() throws AdaStorageException {
        File marker = adaProperties.getNfsMarkerFile();
        if (!marker.exists() || !marker.isFile()) {
            throw new AdaStorageException(
                    "스토리지(NFS)가 마운트되어 있지 않아 업로드할 수 없습니다. 관리자에게 문의해 주세요.");
        }
    }
}

package com.aonfine.ada.dump;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

import javax.annotation.Resource;

import org.springframework.stereotype.Service;

import com.aonfine.ada.common.AdaProperties;

@Service("dumpService")
public class DumpService {

    @Resource(name = "dumpMapper")
    private DumpMapper dumpMapper;

    @Resource
    private AdaProperties adaProperties;

    private Timestamp retentionCutoffNow() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(adaProperties.getRetentionDays());
        return Timestamp.valueOf(cutoff);
    }

    public static final class PageResult {
        public final List<DumpVO> items;
        public final int totalCount;

        public PageResult(List<DumpVO> items, int totalCount) {
            this.items = items;
            this.totalCount = totalCount;
        }
    }

    /** admin이면 전체, 아니면 본인 것만. */
    public PageResult list(DumpSearchVO searchVO, boolean admin, String currentUserId) {
        if (!admin) {
            searchVO.setUserId(currentUserId);
        }
        List<DumpVO> items = dumpMapper.selectList(searchVO, retentionCutoffNow());
        int total = dumpMapper.countList(searchVO);
        return new PageResult(items, total);
    }

    /**
     * 다운로드/조회 접근 권한을 서버에서 검사한다: 본인 것이 아니면 관리자만 허용,
     * 만료된 건은(배치 주기와 무관하게 즉시 재계산) 관리자여도 다운로드를 거절한다.
     */
    public DumpVO getForDownload(String dumpId, boolean admin, String currentUserId) {
        DumpVO vo = dumpMapper.selectOne(dumpId, retentionCutoffNow());
        if (vo == null) {
            return null;
        }
        if (!admin && !vo.getUserId().equals(currentUserId)) {
            return null;
        }
        return vo;
    }

    /** Same ownership/expiry rule as download: analysis acts on the same STORED, non-expired dump. */
    public DumpVO getForAnalysis(String dumpId, boolean admin, String currentUserId) {
        return getForDownload(dumpId, admin, currentUserId);
    }

    public boolean hasActiveUpload(String userId) {
        return dumpMapper.countActiveUploadsByUser(userId) > 0;
    }

    public List<DumpVO> selectExpiredCandidates(int limit) {
        return dumpMapper.selectExpiredCandidates(retentionCutoffNow(), limit);
    }

    public void markCleaned(String dumpId) {
        dumpMapper.markCleaned(dumpId);
    }

    public List<DumpVO> emptyIfNull(List<DumpVO> list) {
        return list == null ? Collections.emptyList() : list;
    }
}

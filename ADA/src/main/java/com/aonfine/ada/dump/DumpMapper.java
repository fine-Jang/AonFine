package com.aonfine.ada.dump;

import java.sql.Timestamp;
import java.util.List;

public interface DumpMapper {

    void insertUploading(DumpVO vo);

    int transition(String dumpId, String expectedStatus, String statusCd, String failReason);

    int markStored(String dumpId, String originalFileNm, String storedFileNm, long fileSize);

    /** cutoff보다 REG_DT가 이전이면 EXPIRED_NOW=true로 즉시 계산해서 반환한다(배치 주기와 무관). */
    DumpVO selectOne(String dumpId, Timestamp cutoff);

    List<DumpVO> selectList(DumpSearchVO searchVO, Timestamp cutoff);

    int countList(DumpSearchVO searchVO);

    int countActiveUploadsByUser(String userId);

    List<DumpVO> selectExpiredCandidates(Timestamp cutoff, int limit);

    void markCleaned(String dumpId);

    /** Compatibility mirror of the JOB/ATTEMPT state on the dump row (ANALYSIS_STATUS_CD). Not the source of truth. */
    void updateAnalysisStatus(String dumpId, String analysisStatusCd);
}

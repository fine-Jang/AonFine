package com.aonfine.ada.dump;

public class DumpSearchVO {
    private String userId;       // null이면 관리자(전체 조회), 값이 있으면 해당 사용자 소유만
    private String keyword;      // 부처명/업무명/서비스명/작업자/파일명 검색어
    private int pageIndex = 1;
    private int pageSize = 20;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public int getPageIndex() {
        return pageIndex;
    }

    public void setPageIndex(int pageIndex) {
        this.pageIndex = pageIndex < 1 ? 1 : pageIndex;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public int getFirstIndex() {
        return (pageIndex - 1) * pageSize;
    }
}

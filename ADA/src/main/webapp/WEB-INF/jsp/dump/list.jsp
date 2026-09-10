<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<!DOCTYPE html>
<html lang="ko">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>업로드 내역 - ADA</title>
  <link rel="stylesheet" type="text/css" href="${pageContext.request.contextPath}/css/ada.css" />
</head>
<body>
  <div class="ada-topbar">
    <img class="ada-logo-sm" src="${pageContext.request.contextPath}/images/ada-logo.png" alt="AONSOFT" />
    <span class="ada-title">ADA - DUMP analyze portal</span>
    <span class="ada-user"><c:out value="${loginUser.userName}" /> (<c:out value="${loginUser.userId}" />)<c:if test="${loginUser.admin}"> · 관리자</c:if></span>
    <a class="ada-logout" href="${pageContext.request.contextPath}/logout.do">로그아웃</a>
  </div>

  <div class="ada-body">
    <div class="ada-sidebar">
      <a href="#" class="ada-menu-item active" id="adaUploadOpenBtn">＋ 덤프 업로드</a>
      <a href="${pageContext.request.contextPath}/dump/list.do" class="ada-menu-item">업로드 내역</a>
    </div>

    <div class="ada-content">
      <h1>업로드 내역</h1>

      <div class="ada-toolbar">
        <div class="ada-toolbar-left">
          <button type="button" class="btn" id="adaDownloadBtn" disabled>원본 덤프 다운로드</button>
          <button type="button" class="btn" id="adaCancelSelectedBtn" disabled>업로드 취소</button>
          <button type="button" class="btn" id="adaReuploadBtn" disabled>재업로드</button>
        </div>
        <div class="ada-toolbar-right">
          <form class="ada-search" method="get" action="${pageContext.request.contextPath}/dump/list.do">
            <input type="text" name="keyword" placeholder="부처명/업무명/호스트명/작업자 검색" value="${fn:escapeXml(keyword)}" />
          </form>
          <button type="button" class="btn" id="adaAnalyzeBtn" disabled>Analyze</button>
          <button type="button" class="btn" id="adaAnalysisCancelBtn" disabled>분석 취소</button>
          <button type="button" class="btn" id="adaAnalysisRetryBtn" disabled>분석 재시도</button>
          <button type="button" class="btn" id="adaAnalyzeResultBtn" disabled>분석 결과 다운로드</button>
        </div>
      </div>

      <p class="ada-help">Worker(MAT/스레드덤프 분석/보고서 생성)는 아직 연결되지 않아 분석 요청은 대기(QUEUED) 상태로 접수만 됩니다. 결과 다운로드 패키징도 준비 중입니다.</p>
      <div id="adaListResult" class="ada-upload-result" role="status" aria-live="polite"></div>
      <div class="ada-table-wrap">
        <table class="ada-table">
          <thead>
            <tr>
              <th></th>
              <th>상태</th>
              <th>작업자</th>
              <th>덤프유형</th>
              <th>부처명</th>
              <th>업무명</th>
              <th>호스트명</th>
              <th>파일명</th>
              <th>파일 크기</th>
              <th>업로드 일시</th>
            </tr>
          </thead>
          <tbody>
            <c:choose>
              <c:when test="${empty dumpList}">
                <tr class="ada-empty-row"><td colspan="10">업로드된 덤프가 없습니다.</td></tr>
              </c:when>
              <c:otherwise>
                <c:forEach var="dump" items="${dumpList}">
                  <tr class="ada-row status-${dump.statusCssKey}"
                      data-dump-id="${dump.dumpId}"
                      data-status="${dump.statusCd}"
                      data-expired="${dump.expiredNow}"
                      data-analysis-status="${dump.analysisStatusCd}">
                    <td><input type="radio" name="dumpSelect" value="${dump.dumpId}" /></td>
                    <td><span class="status-dot"></span><c:out value="${dump.statusLabel}" />
                      <c:if test="${not empty dump.failReason}"><div class="ada-fail-reason"><c:out value="${dump.failReason}" /></div></c:if>
                      <c:if test="${dump.statusCd == 'FAILED' || dump.statusCd == 'CANCELLED'}"><div class="ada-help">파일을 다시 선택하여 재업로드할 수 있습니다.</div></c:if>
                    </td>
                    <td><c:out value="${dump.userId}" /></td>
                    <td><c:out value="${dump.dumpTypeLabel}" /></td>
                    <td><c:out value="${dump.deptNm}" /></td>
                    <td><c:out value="${dump.taskNm}" /></td>
                    <td><c:out value="${dump.hostNm}" /></td>
                    <td><c:out value="${dump.originalFileNm}" /></td>
                    <td><c:out value="${dump.fileSizeLabel}" /></td>
                    <td><c:out value="${dump.regDt}" /></td>
                  </tr>
                </c:forEach>
              </c:otherwise>
            </c:choose>
          </tbody>
        </table>
      </div>

      <div class="ada-pagination">
        <c:forEach begin="1" end="${totalPages}" var="p">
          <c:choose>
            <c:when test="${p == pageIndex}">
              <span class="current">${p}</span>
            </c:when>
            <c:otherwise>
              <a href="${pageContext.request.contextPath}/dump/list.do?pageIndex=${p}&keyword=${fn:escapeXml(keyword)}">${p}</a>
            </c:otherwise>
          </c:choose>
        </c:forEach>
      </div>
    </div>
  </div>

  <!-- 덤프 업로드 팝업 -->
  <div class="ada-modal-backdrop" id="adaUploadModal">
    <div class="ada-modal">
      <h2>덤프 업로드</h2>
      <form id="adaUploadForm">
        <label>덤프 유형 *</label>
        <select name="dumpType" required>
          <option value="" disabled selected>선택하세요</option>
          <option value="HEAP">Heap</option>
          <option value="THREAD">Thread</option>
        </select>
        <label>부처명 *</label>
        <input type="text" name="deptNm" maxlength="200" required />
        <label>업무명 *</label>
        <input type="text" name="taskNm" maxlength="200" required />
        <label>호스트명 *</label>
        <input type="text" name="hostNm" maxlength="200" required />
        <label>덤프 파일 (tar.gz, 5GB 미만) *</label>
        <input type="file" name="dumpFile" accept=".gz,.tar.gz" required />

        <div class="ada-progress-wrap" id="adaProgressWrap">
          <div class="ada-progress-bar-outer"><div class="ada-progress-bar-inner" id="adaProgressBar"></div></div>
          <div class="ada-progress-text" id="adaProgressText"><span>0%</span><span>0 KB / 0 KB</span></div>
        </div>
        <div class="ada-upload-result" id="adaUploadResult" role="status" aria-live="polite"></div>

        <div class="ada-modal-actions">
          <button type="button" class="btn" id="adaUploadCloseBtn">닫기</button>
          <button type="button" class="btn" id="adaUploadCancelBtn" disabled>업로드 취소</button>
          <button type="submit" class="btn btn-primary" id="adaUploadSubmitBtn">업로드</button>
        </div>
      </form>
    </div>
  </div>

  <script>window.ADA_CONTEXT_PATH = "${pageContext.request.contextPath}"; window.ADA_CSRF = "${csrfToken}";</script>
  <script src="${pageContext.request.contextPath}/js/jquery-3.4.1.min.js"></script>
  <script src="${pageContext.request.contextPath}/js/ada-upload.js"></script>

</body>
</html>

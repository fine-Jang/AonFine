<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<!DOCTYPE html>
<html lang="ko">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1, shrink-to-fit=no" />
  <title>공지사항 - AonFine</title>
  <link rel="stylesheet" type="text/css" href="${pageContext.request.contextPath}/css/bootstrap.css" />
  <link href="${pageContext.request.contextPath}/css/font-awesome.min.css" rel="stylesheet" />
  <link href="${pageContext.request.contextPath}/css/style.css" rel="stylesheet" />
  <link href="${pageContext.request.contextPath}/css/responsive.css" rel="stylesheet" />
</head>
<body class="sub_page">
  <div class="hero_area">
    <div class="bg-box"><img src="${pageContext.request.contextPath}/images/hero-bg.jpg" alt=""></div>
    <header class="header_section">
      <div class="container">
        <nav class="navbar navbar-expand-lg custom_nav-container">
          <a class="navbar-brand" href="${pageContext.request.contextPath}/main.do"><span>AonFine</span></a>
          <div class="collapse navbar-collapse show">
            <ul class="navbar-nav mx-auto">
              <li class="nav-item"><a class="nav-link" href="${pageContext.request.contextPath}/main.do">오늘 뭐먹지</a></li>
              <li class="nav-item active"><a class="nav-link" href="${pageContext.request.contextPath}/notice/list.do">공지사항</a></li>
              <li class="nav-item"><a class="nav-link" href="${pageContext.request.contextPath}/lunch/today.do">오늘의 점심</a></li>
              <li class="nav-item"><a class="nav-link" href="${pageContext.request.contextPath}/restaurant/list.do">맛집 목록</a></li>
              <li class="nav-item"><a class="nav-link" href="${pageContext.request.contextPath}/restaurant/form.do">맛집 등록</a></li>
            </ul>
          </div>
        </nav>
      </div>
    </header>
  </div>

  <section class="food_section layout_padding">
    <div class="container">
      <div class="heading_container heading_center">
        <h2>공지사항</h2>
      </div>
      <c:if test="${not empty message}">
        <div class="alert alert-info"><c:out value="${message}" /></div>
      </c:if>
      <form class="row mb-4" action="${pageContext.request.contextPath}/notice/list.do" method="get">
        <div class="col-md-10">
          <input type="text" name="searchKeyword" class="form-control" value="${fn:escapeXml(searchVO.searchKeyword)}" placeholder="제목 또는 내용 검색">
        </div>
        <div class="col-md-2">
          <button type="submit" class="btn btn-warning btn-block">검색</button>
        </div>
      </form>
      <c:if test="${not empty sessionScope.loginUser and sessionScope.loginUser.admin}">
        <div class="text-right mb-3">
          <a href="${pageContext.request.contextPath}/notice/form.do" class="btn btn-warning">공지 등록</a>
        </div>
      </c:if>
      <div class="table-responsive">
        <table class="table table-hover">
          <thead>
            <tr>
              <th>번호</th>
              <th>제목</th>
              <th>작성자</th>
              <th>조회</th>
              <th>등록일</th>
            </tr>
          </thead>
          <tbody>
            <c:forEach var="notice" items="${noticeList}">
              <tr>
                <td><c:out value="${notice.boardId}" /></td>
                <td><a href="${pageContext.request.contextPath}/notice/detail.do?boardId=${notice.boardId}"><c:out value="${notice.title}" /></a></td>
                <td><c:out value="${notice.writerName}" /></td>
                <td><c:out value="${notice.viewCount}" /></td>
                <td><c:out value="${notice.regDt}" /></td>
              </tr>
            </c:forEach>
            <c:if test="${empty noticeList}">
              <tr><td colspan="5" class="text-center">등록된 공지사항이 없습니다.</td></tr>
            </c:if>
          </tbody>
        </table>
      </div>
      <nav class="mt-4">
        <ul class="pagination justify-content-center">
          <c:forEach var="pageNo" begin="${paginationInfo.firstPageNoOnPageList}" end="${paginationInfo.lastPageNoOnPageList}">
            <li class="page-item <c:if test='${pageNo eq paginationInfo.currentPageNo}'>active</c:if>">
              <a class="page-link" href="${pageContext.request.contextPath}/notice/list.do?pageIndex=${pageNo}&searchKeyword=${searchVO.searchKeyword}">${pageNo}</a>
            </li>
          </c:forEach>
        </ul>
      </nav>
    </div>
  </section>
  <script src="${pageContext.request.contextPath}/js/jquery-3.4.1.min.js"></script>
  <script src="${pageContext.request.contextPath}/js/bootstrap.js"></script>
</body>
</html>
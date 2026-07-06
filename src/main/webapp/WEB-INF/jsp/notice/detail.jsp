<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html lang="ko">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1, shrink-to-fit=no" />
  <title>공지사항 상세 - AonFine</title>
  <link rel="stylesheet" type="text/css" href="${pageContext.request.contextPath}/css/bootstrap.css" />
  <link href="${pageContext.request.contextPath}/css/font-awesome.min.css" rel="stylesheet" />
  <link href="${pageContext.request.contextPath}/css/style.css" rel="stylesheet" />
  <link href="${pageContext.request.contextPath}/css/responsive.css" rel="stylesheet" />
</head>
<body class="sub_page">
  <div class="hero_area">
    <div class="bg-box"><img src="${pageContext.request.contextPath}/images/hero-bg.jpg" alt=""></div>
    <header class="header_section"><div class="container"><nav class="navbar navbar-expand-lg custom_nav-container"><a class="navbar-brand" href="${pageContext.request.contextPath}/main.do"><span>AonFine</span></a></nav></div></header>
  </div>

  <section class="about_section layout_padding">
    <div class="container">
      <c:if test="${empty notice}">
        <div class="alert alert-warning">공지사항을 찾을 수 없습니다.</div>
      </c:if>
      <c:if test="${not empty notice}">
        <div class="heading_container">
          <h2><c:out value="${notice.title}" /></h2>
        </div>
        <p class="text-muted">작성자 <c:out value="${notice.writerName}" /> · 조회 <c:out value="${notice.viewCount}" /> · <c:out value="${notice.regDt}" /></p>
        <hr>
        <div style="white-space: pre-wrap; min-height: 220px;"><c:out value="${notice.content}" /></div>
        <hr>
        <a href="${pageContext.request.contextPath}/notice/list.do" class="btn btn-secondary">목록</a>
        <c:if test="${not empty sessionScope.loginUser and sessionScope.loginUser.admin}">
          <a href="${pageContext.request.contextPath}/notice/edit.do?boardId=${notice.boardId}" class="btn btn-warning">수정</a>
          <a href="${pageContext.request.contextPath}/notice/delete.do?boardId=${notice.boardId}" class="btn btn-danger" onclick="return confirm('공지사항을 삭제하시겠습니까?');">삭제</a>
        </c:if>
      </c:if>
    </div>
  </section>
  <script src="${pageContext.request.contextPath}/js/jquery-3.4.1.min.js"></script>
  <script src="${pageContext.request.contextPath}/js/bootstrap.js"></script>
</body>
</html>
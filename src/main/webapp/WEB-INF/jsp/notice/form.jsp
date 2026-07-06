<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<!DOCTYPE html>
<html lang="ko">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1, shrink-to-fit=no" />
  <title>공지사항 등록 - AonFine</title>
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

  <section class="book_section layout_padding">
    <div class="container">
      <div class="heading_container">
        <h2><c:if test="${mode eq 'insert'}">공지 등록</c:if><c:if test="${mode eq 'update'}">공지 수정</c:if></h2>
      </div>
      <c:if test="${not empty message}">
        <div class="alert alert-warning"><c:out value="${message}" /></div>
      </c:if>
      <c:choose>
        <c:when test="${mode eq 'update'}"><c:set var="formAction" value="/notice/update.do" /></c:when>
        <c:otherwise><c:set var="formAction" value="/notice/insert.do" /></c:otherwise>
      </c:choose>
      <div class="row">
        <div class="col-md-8">
          <div class="form_container">
            <form method="post" action="${pageContext.request.contextPath}${formAction}">
              <input type="hidden" name="boardId" value="${notice.boardId}">
              <input type="hidden" name="boardType" value="NOTICE">
              <div>
                <input type="text" name="title" class="form-control" placeholder="제목" value="${fn:escapeXml(notice.title)}" required>
              </div>
              <div>
                <textarea name="content" class="form-control" rows="12" placeholder="내용" required><c:out value="${notice.content}" /></textarea>
              </div>
              <div class="btn_box">
                <button type="submit">저장</button>
                <a href="${pageContext.request.contextPath}/notice/list.do" class="btn btn-secondary">취소</a>
              </div>
            </form>
          </div>
        </div>
      </div>
    </div>
  </section>
  <script src="${pageContext.request.contextPath}/js/jquery-3.4.1.min.js"></script>
  <script src="${pageContext.request.contextPath}/js/bootstrap.js"></script>
</body>
</html>
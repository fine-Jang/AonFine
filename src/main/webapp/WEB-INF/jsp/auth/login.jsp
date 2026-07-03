<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<!DOCTYPE html>
<html lang="ko">
<head>
  <meta charset="utf-8" />
  <meta http-equiv="X-UA-Compatible" content="IE=edge" />
  <meta name="viewport" content="width=device-width, initial-scale=1, shrink-to-fit=no" />
  <title>로그인 - AonFine</title>
  <link rel="shortcut icon" href="${pageContext.request.contextPath}/images/favicon.png" type="">
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
              <li class="nav-item"><a class="nav-link" href="${pageContext.request.contextPath}/restaurant/list.do">맛집 목록</a></li>
              <li class="nav-item"><a class="nav-link" href="${pageContext.request.contextPath}/restaurant/form.do">맛집 등록</a></li>
            </ul>
            <div class="user_option">
              <a href="${pageContext.request.contextPath}/join.do" class="order_online">회원가입</a>
            </div>
          </div>
        </nav>
      </div>
    </header>
  </div>

  <section class="book_section layout_padding">
    <div class="container">
      <div class="heading_container">
        <h2>로그인</h2>
      </div>
      <div class="row">
        <div class="col-md-6">
          <div class="form_container">
            <c:if test="${not empty message}">
              <div class="alert alert-warning"><c:out value="${message}" /></div>
            </c:if>
            <form method="post" action="${pageContext.request.contextPath}/loginProcess.do">
              <input type="hidden" name="returnUrl" value="${fn:escapeXml(returnUrl)}">
              <div>
                <input type="text" name="userId" class="form-control" placeholder="아이디" value="${fn:escapeXml(userId)}" required>
              </div>
              <div>
                <input type="password" name="password" class="form-control" placeholder="패스워드" required>
              </div>
              <div class="btn_box">
                <button type="submit">로그인</button>
                <a href="${pageContext.request.contextPath}/join.do" class="btn btn-secondary">회원가입</a>
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

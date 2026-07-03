<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html lang="ko">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1, shrink-to-fit=no" />
  <title>맛집 상세 - AonFine</title>
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
        </nav>
      </div>
    </header>
  </div>

  <section class="about_section layout_padding">
    <div class="container">
      <c:if test="${empty restaurant}">
        <div class="alert alert-warning">맛집 정보를 찾을 수 없습니다.</div>
      </c:if>
      <c:if test="${not empty restaurant}">
        <div class="row">
          <div class="col-md-6">
            <div class="img-box">
              <c:choose>
                <c:when test="${not empty restaurant.imagePath}">
                  <img src="${pageContext.request.contextPath}${restaurant.imagePath}" alt="">
                </c:when>
                <c:otherwise>
                  <img src="${pageContext.request.contextPath}/images/about-img.png" alt="">
                </c:otherwise>
              </c:choose>
            </div>
          </div>
          <div class="col-md-6">
            <div class="detail-box">
              <div class="heading_container">
                <h2><c:out value="${restaurant.restaurantName}" /></h2>
              </div>
              <p><strong>상호명</strong> <c:out value="${restaurant.storeName}" /></p>
              <p><strong>메뉴</strong> <c:out value="${restaurant.menuName}" /></p>
              <p><strong>주소</strong> <c:out value="${restaurant.address}" /></p>
              <p><c:out value="${restaurant.description}" /></p>
              <a href="${pageContext.request.contextPath}/restaurant/list.do">목록</a>
              <a href="${pageContext.request.contextPath}/restaurant/edit.do?restaurantId=${restaurant.restaurantId}">수정</a>
              <a href="${pageContext.request.contextPath}/restaurant/delete.do?restaurantId=${restaurant.restaurantId}" onclick="return confirm('삭제하시겠습니까?');">삭제</a>
            </div>
          </div>
        </div>
      </c:if>
    </div>
  </section>

  <script src="${pageContext.request.contextPath}/js/jquery-3.4.1.min.js"></script>
  <script src="${pageContext.request.contextPath}/js/bootstrap.js"></script>
</body>
</html>

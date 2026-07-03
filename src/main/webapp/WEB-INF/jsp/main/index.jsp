<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html lang="ko">
<head>
  <meta charset="utf-8" />
  <meta http-equiv="X-UA-Compatible" content="IE=edge" />
  <meta name="viewport" content="width=device-width, initial-scale=1, shrink-to-fit=no" />
  <link rel="shortcut icon" href="${pageContext.request.contextPath}/images/favicon.png" type="">
  <title>AonFine</title>
  <link rel="stylesheet" type="text/css" href="${pageContext.request.contextPath}/css/bootstrap.css" />
  <link href="${pageContext.request.contextPath}/css/font-awesome.min.css" rel="stylesheet" />
  <link href="${pageContext.request.contextPath}/css/style.css" rel="stylesheet" />
  <link href="${pageContext.request.contextPath}/css/responsive.css" rel="stylesheet" />
</head>
<body>
  <div class="hero_area">
    <div class="bg-box">
      <img src="${pageContext.request.contextPath}/images/hero-bg.jpg" alt="">
    </div>
    <header class="header_section">
      <div class="container">
        <nav class="navbar navbar-expand-lg custom_nav-container">
          <a class="navbar-brand" href="${pageContext.request.contextPath}/main.do"><span>AonFine</span></a>
          <button class="navbar-toggler" type="button" data-toggle="collapse" data-target="#navbarSupportedContent">
            <span class=""></span>
          </button>
          <div class="collapse navbar-collapse" id="navbarSupportedContent">
            <ul class="navbar-nav mx-auto">
              <li class="nav-item active"><a class="nav-link" href="${pageContext.request.contextPath}/main.do">오늘 뭐먹지</a></li>
              <li class="nav-item"><a class="nav-link" href="${pageContext.request.contextPath}/restaurant/list.do">맛집 목록</a></li>
              <li class="nav-item"><a class="nav-link" href="${pageContext.request.contextPath}/restaurant/form.do">맛집 등록</a></li>
            </ul>
          </div>
        </nav>
      </div>
    </header>

    <section class="slider_section">
      <div id="customCarousel1" class="carousel slide" data-ride="carousel">
        <div class="carousel-inner">
          <div class="carousel-item active">
            <div class="container">
              <div class="row">
                <div class="col-md-7 col-lg-6">
                  <div class="detail-box">
                    <h1>오늘 뭐먹지?</h1>
                    <p>회사 근처 맛집을 등록하고, 최신 점심 후보를 빠르게 확인하세요.</p>
                    <div class="btn-box">
                      <a href="${pageContext.request.contextPath}/restaurant/form.do" class="btn1">맛집 등록</a>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  </div>

  <section class="food_section layout_padding-bottom">
    <div class="container">
      <div class="heading_container heading_center">
        <h2>최신 맛집</h2>
      </div>
      <div class="filters-content">
        <div class="row grid">
          <c:forEach var="restaurant" items="${latestRestaurants}">
            <div class="col-sm-6 col-lg-4 all">
              <div class="box">
                <div>
                  <div class="img-box">
                    <c:choose>
                      <c:when test="${not empty restaurant.imagePath}">
                        <img src="${pageContext.request.contextPath}${restaurant.imagePath}" alt="">
                      </c:when>
                      <c:otherwise>
                        <img src="${pageContext.request.contextPath}/images/f1.png" alt="">
                      </c:otherwise>
                    </c:choose>
                  </div>
                  <div class="detail-box">
                    <h5><c:out value="${restaurant.restaurantName}" /></h5>
                    <p><c:out value="${restaurant.storeName}" /> · <c:out value="${restaurant.menuName}" /></p>
                    <div class="options">
                      <h6>NEW</h6>
                      <a href="${pageContext.request.contextPath}/restaurant/detail.do?restaurantId=${restaurant.restaurantId}">
                        상세
                      </a>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </c:forEach>
          <c:if test="${empty latestRestaurants}">
            <div class="col-12 text-center">
              <p>등록된 맛집이 없습니다.</p>
            </div>
          </c:if>
        </div>
      </div>
      <div class="btn-box">
        <a href="${pageContext.request.contextPath}/restaurant/list.do">전체 보기</a>
      </div>
    </div>
  </section>

  <script src="${pageContext.request.contextPath}/js/jquery-3.4.1.min.js"></script>
  <script src="${pageContext.request.contextPath}/js/bootstrap.js"></script>
  <script src="${pageContext.request.contextPath}/js/custom.js"></script>
</body>
</html>

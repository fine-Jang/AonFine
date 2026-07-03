<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<!DOCTYPE html>
<html lang="ko">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1, shrink-to-fit=no" />
  <title>맛집 목록 - AonFine</title>
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
              <li class="nav-item active"><a class="nav-link" href="${pageContext.request.contextPath}/restaurant/list.do">맛집 목록</a></li>
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
        <h2>맛집 목록</h2>
      </div>
      <c:if test="${not empty message}">
        <div class="alert alert-info"><c:out value="${message}" /></div>
      </c:if>
      <form class="row mb-4" action="${pageContext.request.contextPath}/restaurant/list.do" method="get">
        <div class="col-md-10">
          <input type="text" name="searchKeyword" class="form-control" value="${fn:escapeXml(searchVO.searchKeyword)}" placeholder="맛집명, 상호명, 메뉴 검색">
        </div>
        <div class="col-md-2">
          <button type="submit" class="btn btn-warning btn-block">검색</button>
        </div>
      </form>

      <div class="filters-content">
        <div class="row grid">
          <c:forEach var="restaurant" items="${restaurantList}">
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
                    <p><c:out value="${restaurant.address}" /></p>
                    <div class="options">
                      <h6><c:out value="${restaurant.menuName}" /></h6>
                      <a href="${pageContext.request.contextPath}/restaurant/detail.do?restaurantId=${restaurant.restaurantId}">상세</a>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </c:forEach>
        </div>
      </div>

      <c:if test="${empty restaurantList}">
        <p class="text-center">등록된 맛집이 없습니다.</p>
      </c:if>

      <nav class="mt-4">
        <ul class="pagination justify-content-center">
          <c:forEach var="pageNo" begin="${paginationInfo.firstPageNoOnPageList}" end="${paginationInfo.lastPageNoOnPageList}">
            <li class="page-item <c:if test='${pageNo eq paginationInfo.currentPageNo}'>active</c:if>">
              <a class="page-link" href="${pageContext.request.contextPath}/restaurant/list.do?pageIndex=${pageNo}&searchKeyword=${searchVO.searchKeyword}">${pageNo}</a>
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

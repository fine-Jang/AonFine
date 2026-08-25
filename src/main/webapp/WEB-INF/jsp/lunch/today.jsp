<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html lang="ko">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1, shrink-to-fit=no" />
  <title>오늘의 점심 - AonFine</title>
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
              <li class="nav-item active"><a class="nav-link" href="${pageContext.request.contextPath}/lunch/today.do">오늘의 점심</a></li>
              <li class="nav-item"><a class="nav-link" href="${pageContext.request.contextPath}/restaurant/list.do">맛집 목록</a></li>
              <li class="nav-item"><a class="nav-link" href="${pageContext.request.contextPath}/restaurant/form.do">맛집 등록</a></li>
            </ul>
            <div class="user_option">
              <c:choose>
                <c:when test="${not empty sessionScope.loginUser}">
                  <span class="text-white mr-3"><c:out value="${sessionScope.loginUser.userName}" />님</span>
                  <a href="${pageContext.request.contextPath}/logout.do" class="order_online">로그아웃</a>
                </c:when>
                <c:otherwise>
                  <a href="${pageContext.request.contextPath}/login.do" class="order_online">로그인</a>
                </c:otherwise>
              </c:choose>
            </div>
          </div>
        </nav>
      </div>
    </header>
  </div>

  <section class="food_section layout_padding">
    <div class="container">
      <div class="heading_container heading_center">
        <h2>오늘의 점심 투표</h2>
      </div>
      <c:if test="${not empty message}">
        <div class="alert alert-info"><c:out value="${message}" /></div>
      </c:if>
      <c:choose>
        <c:when test="${not empty myLunchVote}">
          <div class="alert alert-warning d-flex justify-content-between align-items-center">
            <span>오늘 선택: <strong><c:out value="${myLunchVote.storeName}" /></strong></span>
            <form method="post" action="${pageContext.request.contextPath}/lunch/cancel.do" class="mb-0" onsubmit="return confirm('오늘 점심 선택을 취소하시겠습니까?');">
              <input type="hidden" name="returnUrl" value="/lunch/today.do">
              <button type="submit" class="btn btn-sm btn-outline-secondary">선택 취소</button>
            </form>
          </div>
        </c:when>
        <c:when test="${not empty sessionScope.loginUser}">
          <div class="text-center mb-4">
            <a href="${pageContext.request.contextPath}/lunch/random.do" class="btn btn-warning">랜덤 선택</a>
          </div>
        </c:when>
        <c:otherwise>
          <div class="alert alert-light border text-center">투표와 랜덤 선택은 로그인 후 가능합니다.</div>
        </c:otherwise>
      </c:choose>

      <div class="filters-content">
        <div class="row grid">
          <c:forEach var="item" items="${todayVoteList}">
            <div class="col-sm-6 col-lg-4 all">
              <div class="box">
                <div>
                  <div class="img-box">
                    <c:choose>
                      <c:when test="${not empty item.imagePath}">
                        <img src="${pageContext.request.contextPath}${item.imagePath}" alt="">
                      </c:when>
                      <c:otherwise>
                        <img src="${pageContext.request.contextPath}/images/f1.png" alt="">
                      </c:otherwise>
                    </c:choose>
                  </div>
                  <div class="detail-box">
                    <h5><c:out value="${item.storeName}" /></h5>
                    <p><c:out value="${item.menuName}" /> · <c:out value="${item.address}" /></p>
                    <div class="options">
                      <h6><c:out value="${item.voteCount}" />표</h6>
                      <a href="${pageContext.request.contextPath}/restaurant/detail.do?restaurantId=${item.restaurantId}">상세</a>
                    </div>
                    <c:if test="${not empty sessionScope.loginUser and empty myLunchVote}">
                      <form method="post" action="${pageContext.request.contextPath}/lunch/vote.do" class="mt-3">
                        <input type="hidden" name="restaurantId" value="${item.restaurantId}">
                        <input type="hidden" name="returnUrl" value="/lunch/today.do">
                        <button type="submit" class="btn btn-warning btn-block">오늘 점심 투표</button>
                      </form>
                    </c:if>
                  </div>
                </div>
              </div>
            </div>
          </c:forEach>
        </div>
      </div>
      <c:if test="${empty todayVoteList}">
        <p class="text-center">투표할 맛집이 없습니다.</p>
      </c:if>
    </div>
  </section>

  <script src="${pageContext.request.contextPath}/js/jquery-3.4.1.min.js"></script>
  <script src="${pageContext.request.contextPath}/js/bootstrap.js"></script>
</body>
</html>
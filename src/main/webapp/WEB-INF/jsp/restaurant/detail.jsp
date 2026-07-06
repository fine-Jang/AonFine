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
          <div class="collapse navbar-collapse show">
            <ul class="navbar-nav mx-auto">
              <li class="nav-item"><a class="nav-link" href="${pageContext.request.contextPath}/main.do">오늘 뭐먹지</a></li>
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

  <section class="about_section layout_padding">
    <div class="container">
      <c:if test="${not empty message}">
        <div class="alert alert-info"><c:out value="${message}" /></div>
      </c:if>
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
                <h2><c:out value="${restaurant.storeName}" /></h2>
              </div>
              <p><strong>추천인</strong> <c:out value="${restaurant.restaurantName}" /></p>
              <p><strong>메뉴</strong> <c:out value="${restaurant.menuName}" /></p>
              <p><span class="badge badge-warning"><c:out value="${restaurant.categoryName}" /></span></p>
              <p><strong>주소</strong> <c:out value="${restaurant.address}" /></p>
              <p><c:out value="${restaurant.description}" /></p>
              <a href="${pageContext.request.contextPath}/restaurant/list.do">목록</a>
              <a href="${pageContext.request.contextPath}/restaurant/edit.do?restaurantId=${restaurant.restaurantId}">수정</a>
              <a href="${pageContext.request.contextPath}/restaurant/delete.do?restaurantId=${restaurant.restaurantId}" onclick="return confirm('삭제하시겠습니까?');">삭제</a>
            </div>
          </div>
        </div>

        <div class="row mt-5">
          <div class="col-lg-8">
            <div class="heading_container">
              <h2>댓글</h2>
            </div>
            <c:choose>
              <c:when test="${not empty sessionScope.loginUser}">
                <form method="post" action="${pageContext.request.contextPath}/restaurant/comment/insert.do" class="mb-4">
                  <input type="hidden" name="restaurantId" value="${restaurant.restaurantId}">
                  <div class="form-group">
                    <textarea name="commentText" class="form-control" rows="3" maxlength="1000" placeholder="댓글을 입력하세요." required></textarea>
                  </div>
                  <button type="submit" class="btn btn-warning">댓글 등록</button>
                </form>
              </c:when>
              <c:otherwise>
                <div class="alert alert-light border">
                  댓글 작성은 로그인 후 가능합니다.
                  <a href="${pageContext.request.contextPath}/login.do?returnUrl=/restaurant/detail.do?restaurantId=${restaurant.restaurantId}">로그인</a>
                </div>
              </c:otherwise>
            </c:choose>

            <c:forEach var="comment" items="${commentList}">
              <div class="border-bottom py-3">
                <div class="d-flex justify-content-between align-items-center">
                  <strong><c:out value="${comment.userName}" /></strong>
                  <small class="text-muted"><c:out value="${comment.regDt}" /></small>
                </div>
                <p class="mb-2 mt-2"><c:out value="${comment.commentText}" /></p>
                <c:if test="${not empty sessionScope.loginUser and sessionScope.loginUser.userId eq comment.userId}">
                  <form method="post" action="${pageContext.request.contextPath}/restaurant/comment/delete.do" onsubmit="return confirm('댓글을 삭제하시겠습니까?');">
                    <input type="hidden" name="restaurantId" value="${restaurant.restaurantId}">
                    <input type="hidden" name="commentId" value="${comment.commentId}">
                    <button type="submit" class="btn btn-sm btn-outline-secondary">삭제</button>
                  </form>
                </c:if>
              </div>
            </c:forEach>
            <c:if test="${empty commentList}">
              <p class="text-muted">등록된 댓글이 없습니다.</p>
            </c:if>
          </div>
        </div>
      </c:if>
    </div>
  </section>

  <script src="${pageContext.request.contextPath}/js/jquery-3.4.1.min.js"></script>
  <script src="${pageContext.request.contextPath}/js/bootstrap.js"></script>
</body>
</html>
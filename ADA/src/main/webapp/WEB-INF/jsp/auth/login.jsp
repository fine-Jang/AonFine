<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<!DOCTYPE html>
<html lang="ko">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>ADA - DUMP analyze portal</title>
  <link rel="stylesheet" type="text/css" href="${pageContext.request.contextPath}/css/ada.css" />
</head>
<body>
  <div class="ada-login-wrap">
    <div class="ada-login-left">
      <img class="ada-login-logo" src="${pageContext.request.contextPath}/images/ada-logo.png" alt="AONSOFT" />
      <div class="ada-login-form-area">
        <h1>ADA 로그인</h1>
        <c:if test="${not empty message}">
          <div class="ada-login-message"><c:out value="${message}" /></div>
        </c:if>
        <form method="post" action="${pageContext.request.contextPath}/loginProcess.do">
          <input type="text" name="userId" placeholder="아이디" value="${fn:escapeXml(userId)}" required autofocus />
          <input type="password" name="password" placeholder="비밀번호" required />
          <button type="submit">로그인</button>
        </form>
      </div>
    </div>
    <div class="ada-login-right" style="background-image: url('${pageContext.request.contextPath}/images/ada-login-bg.jpg');">
      <div class="ada-login-right-content">
        <h2>ADA</h2>
        <p>DUMP analyze portal</p>
      </div>
    </div>
  </div>
</body>
</html>

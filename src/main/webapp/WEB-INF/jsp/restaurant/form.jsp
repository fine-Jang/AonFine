<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<!DOCTYPE html>
<html lang="ko">
<head>
  <meta charset="utf-8" />
  <meta http-equiv="X-UA-Compatible" content="IE=edge" />
  <meta name="viewport" content="width=device-width, initial-scale=1, shrink-to-fit=no" />
  <title>맛집 등록 - AonFine</title>
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
              <li class="nav-item active"><a class="nav-link" href="${pageContext.request.contextPath}/restaurant/form.do">맛집 등록</a></li>
            </ul>
          </div>
        </nav>
      </div>
    </header>
  </div>

  <section class="book_section layout_padding">
    <div class="container">
      <div class="heading_container">
        <h2><c:if test="${mode eq 'insert'}">맛집 등록</c:if><c:if test="${mode eq 'update'}">맛집 수정</c:if></h2>
      </div>
      <div class="row">
        <div class="col-md-6">
          <div class="form_container">
            <c:choose>
              <c:when test="${mode eq 'update'}">
                <c:set var="formAction" value="/restaurant/update.do" />
              </c:when>
              <c:otherwise>
                <c:set var="formAction" value="/restaurant/insert.do" />
              </c:otherwise>
            </c:choose>
            <form method="post" enctype="multipart/form-data"
                  action="${pageContext.request.contextPath}${formAction}">
              <input type="hidden" name="restaurantId" value="${restaurant.restaurantId}">
              <input type="hidden" id="latitude" name="latitude" value="${restaurant.latitude}">
              <input type="hidden" id="longitude" name="longitude" value="${restaurant.longitude}">
                            <div>
                <select name="categoryCode" class="form-control" required>
                  <option value="KOREAN" ${restaurant.categoryCode eq 'KOREAN' ? 'selected="selected"' : ''}>한식</option>
                  <option value="CHINESE" ${restaurant.categoryCode eq 'CHINESE' ? 'selected="selected"' : ''}>중식</option>
                  <option value="JAPANESE" ${restaurant.categoryCode eq 'JAPANESE' ? 'selected="selected"' : ''}>일식</option>
                  <option value="WESTERN" ${restaurant.categoryCode eq 'WESTERN' ? 'selected="selected"' : ''}>양식</option>
                  <option value="SNACK" ${restaurant.categoryCode eq 'SNACK' ? 'selected="selected"' : ''}>분식</option>
                  <option value="CAFE" ${restaurant.categoryCode eq 'CAFE' ? 'selected="selected"' : ''}>카페</option>
                  <option value="ETC" ${empty restaurant.categoryCode or restaurant.categoryCode eq 'UNCATEGORIZED' or restaurant.categoryCode eq 'ETC' ? 'selected="selected"' : ''}>기타</option>
                </select>
              </div>
              <div>
                <input type="text" name="restaurantName" class="form-control" placeholder="추천인" value="${fn:escapeXml(restaurant.restaurantName)}" readonly required>
              </div>
              <div class="input-group">
                <input type="text" id="storeName" name="storeName" class="form-control" placeholder="상호명 검색" value="${fn:escapeXml(restaurant.storeName)}" required>
                <div class="input-group-append">
                  <button type="button" class="btn btn-secondary" onclick="searchPlaceByStoreName()">검색</button>
                </div>
              </div>
              <div id="placeSearchResults" class="list-group mb-3" style="display:none;"></div>
              <div id="placeSearchMessage" class="text-muted mb-3" style="display:none;"></div>
              <div>
                <input type="text" id="address" name="address" class="form-control" placeholder="주소" value="${fn:escapeXml(restaurant.address)}" required readonly>
              </div>
              <div>
                <input type="text" name="menuName" class="form-control" placeholder="대표 메뉴" value="${fn:escapeXml(restaurant.menuName)}" required>
              </div>
              <div>
                <input type="file" name="imageFile" class="form-control" accept="image/*">
              </div>
              <div>
                <textarea name="description" class="form-control" rows="4" placeholder="설명"><c:out value="${restaurant.description}" /></textarea>
              </div>
              <div class="btn_box">
                <button type="submit">저장</button>
                <a href="${pageContext.request.contextPath}/restaurant/list.do" class="btn btn-secondary">취소</a>
              </div>
            </form>
          </div>
        </div>
        <div class="col-md-6">
          <div class="map_container">
            <div id="googleMap" style="height: 360px;"></div>
          </div>
        </div>
      </div>
    </div>
  </section>

  <script src="${pageContext.request.contextPath}/js/jquery-3.4.1.min.js"></script>
  <script src="${pageContext.request.contextPath}/js/bootstrap.js"></script>
  <script>
    var restaurantMap;
    var restaurantMarker;
    var geocoder;
    var placeAutocomplete;
    var placesService;

    function initRestaurantMap() {
      var defaultPosition = { lat: 37.5665, lng: 126.9780 };
      var latValue = document.getElementById('latitude').value;
      var lngValue = document.getElementById('longitude').value;
      if (latValue && lngValue) {
        defaultPosition = { lat: parseFloat(latValue), lng: parseFloat(lngValue) };
      }
      geocoder = new google.maps.Geocoder();
      restaurantMap = new google.maps.Map(document.getElementById('googleMap'), {
        zoom: 15,
        center: defaultPosition
      });
      restaurantMarker = new google.maps.Marker({
        map: restaurantMap,
        position: defaultPosition,
        draggable: true
      });
      restaurantMarker.addListener('dragend', function(event) {
        setLocationFields(event.latLng);
        updateAddressByLocation(event.latLng);
      });
      placesService = new google.maps.places.PlacesService(restaurantMap);
      initPlaceAutocomplete();
    }

    function initPlaceAutocomplete() {
      var storeNameInput = document.getElementById('storeName');
      if (!google.maps.places || !storeNameInput) {
        return;
      }
      placeAutocomplete = new google.maps.places.Autocomplete(storeNameInput, {
        fields: ['name', 'formatted_address', 'geometry'],
        types: ['establishment']
      });
      placeAutocomplete.addListener('place_changed', function() {
        var place = placeAutocomplete.getPlace();
        if (!place.geometry || !place.geometry.location) {
          alert('선택한 상호의 위치 정보를 찾을 수 없습니다.');
          return;
        }
        document.getElementById('storeName').value = place.name || storeNameInput.value;
        if (place.formatted_address) {
          document.getElementById('address').value = place.formatted_address;
        }
        restaurantMap.setCenter(place.geometry.location);
        restaurantMarker.setPosition(place.geometry.location);
        setLocationFields(place.geometry.location);
        clearPlaceSearchResults();
      });
    }

    function searchPlaceByStoreName() {
      if (!placesService) {
        alert('Google Places API 설정이 필요합니다.');
        return;
      }
      var keyword = document.getElementById('storeName').value;
      if (!keyword || !keyword.trim()) {
        alert('상호명을 입력하세요.');
        return;
      }

      showPlaceSearchMessage('검색 중입니다.');
      placesService.findPlaceFromQuery({
        query: keyword,
        fields: ['name', 'formatted_address', 'geometry', 'place_id'],
        locationBias: {
          center: restaurantMap.getCenter(),
          radius: 20000
        }
      }, function(results, status) {
        if (status === google.maps.places.PlacesServiceStatus.OK && results.length > 0) {
          renderPlaceSearchResults(results.slice(0, 5));
        } else {
          searchPlaceByText(keyword, status);
        }
      });
    }

    function searchPlaceByText(keyword, previousStatus) {
      placesService.textSearch({
        query: keyword + ' 서울',
        location: restaurantMap.getCenter(),
        radius: 20000
      }, function(results, status) {
        if (status === google.maps.places.PlacesServiceStatus.OK && results.length > 0) {
          renderPlaceSearchResults(results.slice(0, 5));
        } else {
          clearPlaceSearchResults();
          showPlaceSearchMessage('검색 결과가 없습니다. 상태: ' + status + ', 1차 상태: ' + previousStatus + '. Places API 활성화/API 키 제한/결제 설정을 확인하세요.');
        }
      });
    }

    function renderPlaceSearchResults(results) {
      var resultBox = document.getElementById('placeSearchResults');
      var messageBox = document.getElementById('placeSearchMessage');
      resultBox.innerHTML = '';
      results.forEach(function(place) {
        var button = document.createElement('button');
        button.type = 'button';
        button.className = 'list-group-item list-group-item-action';
        button.innerHTML = '<strong>' + escapeHtml(place.name || '') + '</strong><br><small>' + escapeHtml(place.formatted_address || '') + '</small>';
        button.onclick = function() {
          selectPlaceResult(place);
        };
        resultBox.appendChild(button);
      });
      messageBox.style.display = 'none';
      resultBox.style.display = 'block';
    }

    function selectPlaceResult(place) {
      if (!place.geometry || !place.geometry.location) {
        alert('선택한 상호의 위치 정보를 찾을 수 없습니다.');
        return;
      }
      document.getElementById('storeName').value = place.name || document.getElementById('storeName').value;
      document.getElementById('address').value = place.formatted_address || '';
      restaurantMap.setCenter(place.geometry.location);
      restaurantMarker.setPosition(place.geometry.location);
      setLocationFields(place.geometry.location);
      clearPlaceSearchResults();
    }

    function setLocationFields(location) {
      document.getElementById('latitude').value = location.lat();
      document.getElementById('longitude').value = location.lng();
    }

    function updateAddressByLocation(location) {
      geocoder.geocode({ location: location }, function(results, status) {
        if (status === 'OK' && results.length > 0) {
          document.getElementById('address').value = results[0].formatted_address;
        }
      });
    }

    function clearPlaceSearchResults() {
      document.getElementById('placeSearchResults').style.display = 'none';
      document.getElementById('placeSearchResults').innerHTML = '';
      document.getElementById('placeSearchMessage').style.display = 'none';
    }

    function showPlaceSearchMessage(message) {
      document.getElementById('placeSearchResults').style.display = 'none';
      document.getElementById('placeSearchResults').innerHTML = '';
      document.getElementById('placeSearchMessage').innerText = message;
      document.getElementById('placeSearchMessage').style.display = 'block';
    }

    function escapeHtml(value) {
      return String(value).replace(/[&<>"']/g, function(char) {
        return {
          '&': '&amp;',
          '<': '&lt;',
          '>': '&gt;',
          '"': '&quot;',
          "'": '&#39;'
        }[char];
      });
    }
  </script>
  <script async defer src="https://maps.googleapis.com/maps/api/js?key=AIzaSyDv4Wsoqr2o4o-kSMNrr9kDq62DmsQ7Dg4&libraries=places&callback=initRestaurantMap"></script>
</body>
</html>


// to get current year
function getYear() {
    var currentDate = new Date();
    var currentYear = currentDate.getFullYear();
    var displayYear = document.querySelector("#displayYear");
    if (displayYear) {
        displayYear.innerHTML = currentYear;
    }
}

getYear();


// isotope js
$(window).on('load', function () {
    var $grid = $(".grid");
    var hasIsotope = $.fn.isotope && $grid.length > 0;

    if (hasIsotope) {
        $grid.isotope({
            itemSelector: ".all",
            percentPosition: false,
            masonry: {
                columnWidth: ".all"
            }
        });
    }

    $('.filters_menu li').click(function () {
        $('.filters_menu li').removeClass('active');
        $(this).addClass('active');

        var data = $(this).attr('data-filter');
        if (hasIsotope) {
            $grid.isotope({ filter: data });
        } else {
            if (data === '*') {
                $grid.find('.all').show();
            } else {
                $grid.find('.all').hide();
                $grid.find(data).show();
            }
        }
    });

    var categoryMenu = document.getElementById('todayMenuCategories');
    var prevButton = document.getElementById('categoryPrev');
    var nextButton = document.getElementById('categoryNext');
    if (categoryMenu && prevButton && nextButton) {
        var scrollStep = 102;
        prevButton.addEventListener('click', function () {
            categoryMenu.scrollLeft -= scrollStep;
        });
        nextButton.addEventListener('click', function () {
            categoryMenu.scrollLeft += scrollStep;
        });
    }
});

// nice select
$(document).ready(function() {
    if ($.fn.niceSelect) {
        $('select').niceSelect();
    }
  });

/** google_map js **/
function myMap() {
    var mapProp = {
        center: new google.maps.LatLng(40.712775, -74.005973),
        zoom: 18,
    };
    var map = new google.maps.Map(document.getElementById("googleMap"), mapProp);
}

// client section owl carousel
if ($.fn.owlCarousel && $(".client_owl-carousel").length > 0) {
$(".client_owl-carousel").owlCarousel({
    loop: true,
    margin: 0,
    dots: false,
    nav: true,
    navText: [],
    autoplay: true,
    autoplayHoverPause: true,
    navText: [
        '<i class="fa fa-angle-left" aria-hidden="true"></i>',
        '<i class="fa fa-angle-right" aria-hidden="true"></i>'
    ],
    responsive: {
        0: {
            items: 1
        },
        768: {
            items: 2
        },
        1000: {
            items: 2
        }
    }
});
}

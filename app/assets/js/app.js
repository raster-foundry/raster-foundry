$(function() {
	///
	/// Closable Bootstrap Tabs
	$('#sidebar-nav a').click(function () {
        var tab = $(this);

	    if(tab.parent('li').hasClass('active')) {
        setTimeout(function() {
        		// Remove class from nav-bar tab
            tab.parent('li').removeClass('active');
            // Removes class from tab pane which hides toolbar and utility panel
          	$(".tab-pane").removeClass('active');
        }, 1);
	    }
	});

	///
	/// Floating input labels
	$('.form-group.floating-label .form-control').change(function() {
    if($(this).val() == ""){
      $(this).siblings('label').removeClass('float-label');
    }
    else { 
      $(this).siblings('label').addClass('float-label');
    }
	});


	///
	/// Active class on checkboxes
	$('.checkbox input').click(function() {
		var checkboxInput = $(this);
		var checkboxParent = $(this).parent('.checkbox');
		
		if($(checkboxParent).hasClass('active')){
			$(checkboxParent).removeClass('active');
			$(this).attr('checked', false);
		} else {
			$(checkboxParent).addClass('active');
			$(this).attr('checked', true);
		}
	});

	/*$('.togglable').click(function(e){
    if( e.target.nodeName === '.list-group-hover' ) {
        e.stopPropagation();
        return;
    }

    var checkboxParent = $(this).find('.checkbox');
    var checkboxInput = $(checkboxParent).find('input[type="checkbox"]');

		if( $(checkboxParent).hasClass('active')) {
			$(checkboxParent).removeClass('active');
			$(checkboxInput).attr('checked', false);
		} else {
			$(checkboxParent).addClass('active');
			$(checkboxInput).attr('checked', true);
		}
	});*/


	$('[data-toggle-pane]').click(function(evt) {
    evt.preventDefault()

    var paneTarget = $(this).data('pane-target'),
        paneParent = $(this).closest('.pane')

    if ($(paneParent).hasClass('active')) {
    	$(paneParent).removeClass('active').hide()
    	$(paneTarget).addClass('active');
    }
  });


  ///
  /// Temp activation for layer-details panel
  $('.list-group-hover').click(function(evt) {
    evt.preventDefault()

    $('.layer-detail').addClass('active');
  });

  $('.layer-detail .close').click(function(evt) {
    evt.preventDefault()

    $('.layer-detail').removeClass('active');
  });

});


$(function () {
    $('[data-toggle="tooltip"]').tooltip()
})





/*/// Useful for resizing
var setupSize = function() {
    var bottomPadding = 10;

    var resize = function(){
        var pane = $('#main');
        var height = $(window).height() - pane.offset().top - bottomPadding;
        pane.css({'height': height +'px'});

        var sidebar = $('#tabBody');
        var height = $(window).height() - sidebar.offset().top - bottomPadding;
        sidebar.css({'height': height +'px'});

        var mapDiv = $('#map');
		var wrapDiv = $('#wrap');
        var height = $(window).height() - mapDiv.offset().top - bottomPadding - wrapDiv.height();
        mapDiv.css({'height': height +'px'});
        map.invalidateSize();
    };
    resize();
    $(window).resize(resize);
};

// On page load
$(document).ready(function() {
    // Set heights

    weightedOverlay.bindSliders();
    colorRamps.bindColorRamps();

    $('#clearButton').click( function() {
        summary.clear();
        return false;
    });
    setupSize();
});
*/
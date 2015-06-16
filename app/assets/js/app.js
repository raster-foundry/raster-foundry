///
/// Bootstrap Module expansion
/// Closable Bootstrap Tabs
$(function() {
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
});

///
/// Floating input labels
$(function() {
	$('.form-group.floating-label .form-control').change(function() {
        if($(this).val() == ""){
          $(this).siblings('label').removeClass('float-label');
        }
        else { 
          $(this).siblings('label').addClass('float-label');
        }
	});
});


///
/// Active class on checkboxes
$(function() {
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
});


///
/// Toggles between modal panels
$(function() {
	$('[data-toggle-pane]').click(function(evt) {
        evt.preventDefault()

        var paneTarget = $(this).data('pane-target'),
            paneParent = $(this).closest('.pane')

        if ($(paneParent).hasClass('active')) {
        	$(paneParent).removeClass('active').hide()
        	$(paneTarget).addClass('active');
        }
    });
});


///
/// Temp activation for layer-details panel and Image Metadata
$(function() {
    var layerDetail = $('.layer-detail');

    $('.list-group-hover').click(function(evt) {
        evt.preventDefault()

        layerDetail.addClass('active');
    });

    $('.layer-detail .close').click(function(evt) {
        evt.preventDefault()

        layerDetail.addClass('slideOutLeft')
            setTimeout(function() {
                layerDetail.removeClass('slideOutLeft active')
            }, 400);
    });

});


///
/// Temp activation for layer-details panel and Image Metadata
$(function() {
    var imageMetadata = $('.image-metadata');

    $('.view-metadata').click(function(evt) {
        evt.preventDefault()

        imageMetadata.addClass('active');
    });

    $('.image-metadata .close').click(function(evt) {
        evt.preventDefault()

        imageMetadata.addClass('slideOutLeft')
            setTimeout(function() {
                imageMetadata.removeClass('slideOutLeft active')
            }, 400);
    });

});


///
/// Bootstrap Module
/// Activates tooltips
$(function () {
    $('[data-toggle="tooltip"]').tooltip()
});


///
/// Controls resizing layers panel
var setupSize = function() {
    //var bottomPadding = 10;

    var resize = function(){
        var sidebar = $('.sidebar');

        var sidebarHeader = $('.sidebar-header');

        var sidebarUtilHeader = $('.sidebar-utility-header');

        var sidebarUtilToolbar = $('.sidebar-utility-toolbar');

        var resizeListGroup = $('.sidebar .list-group');
        var resizeLayerDetails = $('.layer-detail-content');

        var height = sidebar.height() - sidebarHeader.height() - sidebarUtilHeader.height() - sidebarUtilToolbar.height() - 30;
        
        resizeListGroup.css({'max-height': height + 'px'});
        resizeLayerDetails.css({'max-height': height + 'px'});
    };
    resize();
    $(window).resize(resize);
};

// On page load
$(document).ready(function() {
    // Set heights
    setupSize();
});
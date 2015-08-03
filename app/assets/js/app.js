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
/// Toggles between modal panes
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
/// Temp activation for layer-details panel
$(function() {
    var layerDetail = $('.layer-detail');

    $('.list-group-item .list-group-link').click(function(evt) {
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
/// Temp activation for Image Metadata
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
/// Temp activation for secondary layer tools
$(function() {
    $('.select-all').click(function(){
        $(this).parent('.utility-tools-secondary').toggleClass('active');
    });
});


///
/// Bootstrap Module
/// Activates tooltips
$(function () {
    $('[data-toggle="tooltip"]').tooltip({
        container: '.sidebar-utility-content',
        viewport: '.sidebar'
    })
});


///
/// Controls resizing layers panel
var setupSize = function() {

    var resize = function(){
        var sidebar = $('.sidebar');

        var sidebarHeader = $('.sidebar-header');

        var sidebarUtilHeader = $('.sidebar-utility-header');

        var sidebarUtilToolbar = $('.sidebar-utility-toolbar');

        var resizeListGroup = $('.sidebar .list-group');
        var resizeLayerDetails = $('.layer-detail .layer-detail-content, .image-metadata .layer-detail-content');

        var height = sidebar.height() - sidebarHeader.height() - sidebarUtilHeader.height() - sidebarUtilToolbar.height() - 30;

        var heightSecondary = sidebar.height() - sidebarHeader.height() - sidebarUtilToolbar.height() - 20;
        
        resizeListGroup.css({'max-height': height + 'px'});
        resizeLayerDetails.css({'max-height': heightSecondary + 'px'});
    };
    resize();
    $(window).resize(resize);
};

// On page load
$(document).ready(function() {
    // Set heights
    setupSize();
});
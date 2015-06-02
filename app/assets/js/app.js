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
	$('.form-group .form-control').change(function() {
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

    $(paneParent).removeClass('active').hide()
      $('.pane-form').find(paneTarget).show()
      setTimeout(function() { 
        $('.pane-form').find(paneTarget).addClass('active')
      }, 10);
    
  });
});
$(function() {
  
  /* 
   * Bootstrap plugins
   */
  $('[data-toggle="tooltip"]').tooltip();


  /* 
   * Prototype functionality
   */
  $('[data-toggle-hide]').on('click', function(el){
    el.preventDefault;

    $target = $(this).attr('data-target');
    $($target).toggleClass('hide');
  });


  $('[data-item-select]').click(function(){
    var $newClass = $(this).attr('data-newClass');
    $(this).toggleClass($newClass);
  });


  /* 
   * Filters
   * http://refreshless.com/nouislider/ 
   */
  function filter_yearSlider() {
    var year_slider = document.getElementById('year_slider')
    noUiSlider.create(year_slider, {
      start: [ 2013, 2016 ], // Handle start position
      connect: true, // Display a colored bar between the handles
      behaviour: 'tap-drag', // Move handle on tap, bar is draggable
      step: 1,
      range: {
        'min': [2013],
        'max': [2016]
      },
      pips: {
        mode: 'count',
        values: 4
      }
    });
    return this;
  };
  filter_yearSlider();

  function filter_cloudSlider() {
    var cloud_slider = document.getElementById('cloud_slider')
    noUiSlider.create(cloud_slider, {
      start: [ 0, 100 ],
      connect: true,
      behaviour: 'tap-drag',
      step: 10,
      range: {
        'min': [0],
        'max': [100]
      },
      pips: {
        mode: 'count',
        values: 11
      }
    });
    return this;
  };
  filter_cloudSlider();

  function filter_sunSlider() {
    var sun_slider = document.getElementById('sun_slider')
    noUiSlider.create(sun_slider, {
      start: [ 0, 180 ],
      connect: true,
      behaviour: 'tap-drag',
      step: 10,
      range: {
        'min': [0],
        'max': [180]
      },
      pips: {
        mode: 'count',
        values: 7
      }
    });
    return this;
  };
  filter_sunSlider();
});
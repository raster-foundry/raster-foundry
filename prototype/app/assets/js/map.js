$(function() {

  // Leaflet map
  var mymap = L.map('map', {
    zoomControl: false
  }).setView([26.8625, -87.8467], 3);
  
  var cartoPositron = L.tileLayer('http://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}.png', {
    attribution: '&copy; <a href="http://www.openstreetmap.org/copyright">OpenStreetMap</a> &copy; <a href="http://cartodb.com/attributions">CartoDB</a>',
    maxZoom: 19
  });

  var commandCenter = L.control({position: 'topright'});

      commandCenter.onAdd = function (map) {
        var div = L.DomUtil.create('div', 'map-control-panel');

        div.innerHTML = 
          '<button class="btn btn-default"><i class="icon-resize-full"></i></button>' +
          '<hr>' +
          '<button type="button" class="btn btn-default btn-block" data-dismiss="modal" data-toggle="modal" data-target="#searchModal"><i class="icon-search"></i> Find places</button>'; 
        return div;
      };

  var zoom = L.control.zoom({ position: 'topright' });

  cartoPositron.addTo(mymap);
  commandCenter.addTo(mymap);
  zoom.addTo(mymap);

  $zoom = $('.leaflet-control-zoom'),
  $mpc  = $('.map-control-panel');
  
  $($mpc).prepend($zoom);
});
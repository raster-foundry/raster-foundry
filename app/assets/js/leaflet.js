/// Leaflet JS base map
///

var map = L.map('map', { zoomControl:false }).setView([39.9500, -75.1667], 13);

L.tileLayer ('http://{s}.tiles.mapbox.com/v3/mpwilliams89.lodmp50l/{z}/{x}/{y}.png', 
	{
   	attribution: 'Raster Foundry | Map data &copy; <a href="http://openstreetmap.org">OpenStreetMap</a> contributors, <a href="http://creativecommons.org/licenses/by-sa/2.0/">CC-BY-SA</a>, Imagery © <a href="http://mapbox.com">Mapbox</a>',
    maxZoom: 18,
	}).addTo(map);

L.control.zoom ({
	position: 'bottomright'
}).addTo(map);
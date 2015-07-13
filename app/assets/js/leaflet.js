/// Leaflet JS base map
///

var map = L.map('map', { zoomControl:false }).setView([39.9500, -75.1667], 13);

L.tileLayer ('http://{s}.basemaps.cartocdn.com/light_nolabels/{z}/{x}/{y}.png', 
	{
   	attribution: 'Raster Foundry | Map data &copy; <a href="http://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors, &copy; <a href="http://cartodb.com/attributions">CartoDB</a>',
    maxZoom: 18,
	}).addTo(map);

L.control.zoom ({
	position: 'bottomright'
}).addTo(map);
// LeafletMap controller class
export default class LeafletMapController {
    constructor($log, $timeout, $element) {
        'ngInject';

        const map = L.map($element[0].children[0], {zoomControl: false})
              .setView([26.8625, -87.8467], 3);

        let cartoPositron = L.tileLayer(
            'http://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}.png', {
                attribution: '&copy; <a href="http://www.openstreetmap.org/copyright">' +
                    'OpenStreetMap</a> &copy;<a href="http://cartodb.com/attributions">CartoDB</a>',
                maxZoom: 19
            }
        );
        let commandCenter = L.control({position: 'topright'});
        commandCenter.onAdd = function () {
            let div = L.DomUtil.create('div', 'map-control-panel');

            div.innerHTML =
                '<button class="btn btn-default"><i class="icon-resize-full"></i></button>' +
                '<hr>' +
                '<button class="btn btn-default btn-block"><i class="icon-search">' +
                '</i> Find places</button>';
            return div;
        };
        let zoom = L.control.zoom({position: 'topright'});
        cartoPositron.addTo(map);
        commandCenter.addTo(map);
        zoom.addTo(map);

        $timeout(function () {
            map.invalidateSize();
        }, 400);
    }
}


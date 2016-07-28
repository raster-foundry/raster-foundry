// LeafletMap controller class
export default class LeafletMapController {
    constructor($log, $timeout, $element) {
        'ngInject';

        $log.log('Leaflet Map component initializing');
        const map = L.map($element[0].children[0], {zoomControl: false})
            .setView([39.9500, -75.1667], 13);
        L.tileLayer(
            'http://{s}.basemaps.cartocdn.com/light_nolabels/{z}/{x}/{y}.png',
            {
                attribution:
                'Raster Foundry | Map data &copy;' +
                    ' <a href="http://www.openstreetmap.org/copyright">OpenStreetMap' +
                    '</a> contributors, &copy;' +
                    ' <a href="http://cartodb.com/attributions">CartoDB</a>',
                maxZoom: 18
            }).addTo(map);
        L.control.zoom({
            position: 'bottomright'
        }).addTo(map);
        // when loading for the first time, map can get initialized
        // before the parent component sets the size correctly.
        $timeout(function () {
            map.invalidateSize();
        }, 400);

        $log.log('Leaflet Map component initialized.');
    }
}


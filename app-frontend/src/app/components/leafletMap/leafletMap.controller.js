// LeafletMap controller class
export default class LeafletMapController {
    constructor($log, $timeout, $element, $scope) {
        'ngInject';

        this.$element = $element;
        this.$timeout = $timeout;
        this.$scope = $scope;

        this.initMap();
        this.initLayers();

        $scope.$watch(() => this.footprint, (newVal) => {
            if (newVal) {
                let geojsonFeature = {
                    type: 'Feature',
                    properties: {
                        name: 'Scene Footprint'
                    },
                    geometry: newVal
                };
                this.geojsonLayer.clearLayers();
                this.geojsonLayer.addData(geojsonFeature);
                this.map.fitBounds(this.geojsonLayer.getBounds());
            }
        });
    }

    initLayers() {
        this.geojsonLayer = L.geoJSON().addTo(this.map);
    }

    initMap() {
        this.map = L.map(this.$element[0].children[0], {
            zoomControl: false,
            scrollWheelZoom: !this.static,
            doubleClickZoom: !this.static,
            dragging: !this.static,
            touchZoom: !this.static,
            boxZoom: !this.static,
            keyboard: !this.static,
            tap: !this.static
        }).setView([26.8625, -87.8467], 3);

        let cartoPositron = L.tileLayer(
            'https://cartodb-basemaps-{s}.global.ssl.fastly.net/light_all/{z}/{x}/{y}.png', {
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

        cartoPositron.addTo(this.map);

        if (!this.static) {
            let zoom = L.control.zoom({position: 'topright'});
            commandCenter.addTo(this.map);
            zoom.addTo(this.map);

            let $zoom = this.$element.find('.leaflet-control-zoom');
            let $mpc = this.$element.find('.map-control-panel');
            $mpc.prepend($zoom);
        }

        this.$timeout(() => {
            this.map.invalidateSize();
        }, 400);
    }
}

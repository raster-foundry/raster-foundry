// LeafletMap controller class
export default class LeafletMapController {
    constructor($log, $timeout, $element, $scope) {
        'ngInject';

        this.$element = $element;
        this.$timeout = $timeout;
        this.$scope = $scope;
        this.$log = $log;

        this.initMap();
        this.initLayers();
    }

    $onInit() {
        this.map.on('moveend', () => this.boundsChangeListener());
    }

    $onChanges(changes) {
        if (changes.footprint) {
            if (changes.footprint.currentValue) {
                let geojsonFeature = {
                    type: 'Feature',
                    properties: {
                        name: 'Scene Footprint'
                    },
                    geometry: changes.footprint.currentValue
                };
                this.geojsonLayer.clearLayers();
                this.geojsonLayer.addData(geojsonFeature);
                if (!this.bypassFitBounds) {
                    this.map.fitBounds(this.geojsonLayer.getBounds());
                }
            } else {
                this.geojsonLayer.clearLayers();
            }
        }
        if (changes.proposedBounds && changes.proposedBounds.currentValue) {
            this.map.fitBounds(changes.proposedBounds.currentValue);
        }

        // Add layers to map when a change is detected
        if (changes.layers && changes.layers.currentValue) {
            for (const layer of this.layers) {
                layer.tiles.addTo(this.map);
            }
        }
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
        // fitBounds won't work without calling setView first.
        }).setView([0, 0], 2);

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

    boundsChangeListener() {
        this.onBoundsChange({newBounds: this.map.getBounds()});
    }
}

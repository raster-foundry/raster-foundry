// LeafletMap controller class
function calculateArea(layer) {
    return L.GeometryUtil.geodesicArea(layer.getLatLngs()[0]) / 1000000;
}
export default class LeafletMapController {
    constructor($log, $timeout, $element, $scope) {
        'ngInject';

        this.$element = $element;
        this.$timeout = $timeout;
        this.$scope = $scope;
        this.$log = $log;

        this.initMap();
        this.initLayers();

        this.$scope.$watchCollection('$ctrl.drawnPolygons', this.onDrawnPolygonsChange.bind(this));
    }

    $onInit() {
        this.map.on('moveend', () => this.boundsChangeListener());
        this.map.on('click', (ev) => this.mapClickListener(ev));
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

        if (changes.grid && changes.grid.currentValue) {
            this.gridLayer.clearLayers();
            this.gridLayer.addData(changes.grid.currentValue);
        }

        // Add a highlight to the map
        if (changes.highlight) {
            if (changes.highlight.previousValue && !changes.highlight.isFirstChange()) {
                changes.highlight.previousValue.removeFrom(this.map);
            }
            if (changes.highlight.currentValue) {
                changes.highlight.currentValue.addTo(this.map);
            }
        }
        if (changes.allowDrawing && typeof changes.allowDrawing.currentValue !== 'undefined') {
            if (changes.allowDrawing.currentValue) {
                this.drawControl.addTo(this.map);
            } else {
                this.drawControl.remove();
            }
        }
    }

    // TODO: on refactor, track layers by id so that they don't need to all be deleted / added
    //       every time there's an update.
    onDrawnPolygonsChange(polygons) {
        this.drawLayer.clearLayers();
        if (polygons && polygons.length) {
            for (const polygon of polygons) {
                this.drawLayer.addData(polygon);
            }
        }
    }

    initLayers() {
        this.geojsonLayer = L.geoJSON().addTo(this.map);
        this.gridLayer = L.geoJson(null, {
            style: () => {
                return {
                    fillOpacity: 0,
                    color: '#465076',
                    weight: 0.5
                };
            },
            onEachFeature: (feature, layer) => {
                let count = feature.properties.count;
                layer.bindTooltip(`${count}`);
            }
        }).addTo(this.map);
        this.highlightLayer = L.geoJSON().addTo(this.map);
        this.drawLayer = L.geoJSON(null, {
            style: () => {
                return {
                    dashArray: '10, 10',
                    weight: 2,
                    fillOpacity: 0.2
                };
            }
        }).addTo(this.map);

        let drawCtlOptions = {
            position: 'topleft',
            draw: {
                polyline: false,
                marker: false,
                circle: false,
                rectangle: {
                    shapeOptions: {
                        dashArray: '10, 10',
                        weight: 2,
                        fillOpacity: 0.2
                    }
                },
                polygon: {
                    finish: false,
                    allowIntersection: false,
                    drawError: {
                        color: '#e1e100',
                        message: 'Invalid mask polygon'
                    },
                    shapeOptions: {
                        dashArray: '10, 10',
                        weight: 2,
                        fillOpacity: 0.2
                    }
                }
            },
            edit: {
                featureGroup: this.drawLayer,
                remove: true
            }
        };
        this.drawControl = new L.Control.Draw(drawCtlOptions);
        if (this.allowDrawing) {
            this.map.addControl(this.drawControl);
        }
        this.map.on(L.Draw.Event.CREATED, (e) => {
            let geoJson = e.layer.toGeoJSON();
            geoJson.properties.area = calculateArea(e.layer);
            // TODO : remove this later - It's just to uniquely identify polygons for now
            // We will want to use the UUID for the polygon we get from our endpoint request
            geoJson.properties.createdAt = new Date();
            this.drawnPolygons.push(geoJson);
            this.onDrawnPolygonsChange(this.drawnPolygons);
            this.$scope.$apply();
        });

        // TODO: During the refactor, use e.layers.eachLayer if it makes more sense.
        this.map.on(L.Draw.Event.EDITED, (e) => {
            Object.values(
                e.layers._layers // eslint-disable-line no-underscore-dangle
            ).forEach((layer) => {
                let newPolygon = layer.toGeoJSON();
                let oldPolygonIndex = this.drawnPolygons.findIndex(
                    (polygon) => polygon.properties.area === newPolygon.properties.area
                );
                newPolygon.properties.area = calculateArea(layer);
                this.drawnPolygons.splice(oldPolygonIndex, 1);
                // TODO: drawn polygons should probably be a Map to make adding/removing/updating
                //       more conceptually simple.
                //       eg: this.drawnPolygons.delete(..); this.drawnPolygons.set(...)
                //       instead of the weirdly named array equivalents.
                this.drawnPolygons.push(newPolygon);
            });
            this.onDrawnPolygonsChange(this.drawnPolygons);
            this.$scope.$apply();
        });

        // TODO: Same as with above
        this.map.on(L.Draw.Event.DELETED, (e) => {
            Object.values(
                e.layers._layers // eslint-disable-line no-underscore-dangle
            ).forEach((layer) => {
                let area = layer.feature.properties.area;
                let polygonIndex = this.drawnPolygons.findIndex(
                    (polygon) => polygon.properties.area === area
                );
                this.drawnPolygons.splice(polygonIndex, 1);
            });
            this.onDrawnPolygonsChange(this.drawnPolygons);
            this.$scope.$apply();
        });
    }

    initMap() {
        this.map = L.map(this.$element[0].children[0], {
            zoomControl: false,
            worldCopyJump: true,
            minZoom: 2,
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

    // Listeners to provide interaction hooks to enclosing components
    boundsChangeListener() {
        this.onViewChange({newBounds: this.map.getBounds(), zoom: this.map.getZoom()});
    }

    mapClickListener(clickEvent) {
        this.onMapClick({event: clickEvent});
    }
}

/* globals BUILDCONFIG */
import angular from 'angular';
import _ from 'lodash';
import mapTpl from './mapContainer.html';
import ShapeActions from '_redux/actions/shape-actions';

require('leaflet/dist/leaflet.css');
require('leaflet-draw/dist/leaflet.draw.css');
require('leaflet-draw/dist/leaflet.draw.js');
require('leaflet-path-drag/dist/L.Path.Drag-src.js');
require('leaflet-draw-drag/dist/Leaflet.draw.drag-src.js');
require('leaflet-path-transform/dist/L.Path.Transform-src.js');

const GREEN = '#81C784';

const MapContainerComponent = {
    templateUrl: mapTpl,
    controller: 'MapContainerController',
    bindings: {
        mapId: '@',
        options: '<?',
        initialCenter: '<',
        initialZoom: '<'
    }
};

class MapContainerController {
    constructor(
        $document, $element, $scope, $timeout, $q,
        modalService, mapService, $ngRedux, uuid4
    ) {
        'ngInject';
        this.$document = $document;
        this.$element = $element;
        this.$scope = $scope;
        this.$timeout = $timeout;
        this.$q = $q;
        this.modalService = modalService;
        this.mapService = mapService;
        this.uuid4 = uuid4;
        this.getMap = () => this.mapService.getMap(this.mapId);

        let unsubscribe = $ngRedux.connect(
            this.mapStateToThis.bind(this),
            ShapeActions
        )(this);
        $scope.$on('$destroy', unsubscribe);

        this.getMap().then(m => this.initDrawControls(m));

        this.$scope.$watch('$ctrl.drawing', (newval, oldval) => {
            if (newval !== oldval) {
                this.mapWrapper.toggleFullscreen(newval);
                this.shapes = {
                    type: 'FeatureCollection',
                    features: []
                };
                if (newval) {
                    this.drawListeners = [
                        this.mapWrapper.on(L.Draw.Event.CREATED, this.createShape.bind(this))
                    ];
                } else {
                    this.shapeInProgress = false;
                    this.drawPolygonHandler.disable();
                    this.drawListeners.forEach(l => {
                        this.mapWrapper.off(l);
                    });
                    this.drawListeners = [];
                }
            }
        });
    }

    mapStateToThis(state) {
        const drawing = state.shape.mapId === this.mapId;
        return {
            drawing,
            drawResolve: state.shape.resolve,
            drawReject: state.shape.resolve
        };
    }

    $postLink() {
        this.initMap();
    }

    $onChanges(changes) {
        if (changes.options && changes.options.currentValue) {
            this.getMap().then((mapWrapper) => {
                mapWrapper.changeOptions(changes.options.currentValue);
            });
        }
    }

    $onDestroy() {
        this.mapService.deregisterMap(this.mapId);
        delete this.mapWrapper;
        if (this.clickListener) {
            this.$document.off('click', this.clickListener);
        }
    }

    initDrawControls(mapWrapper) {
        this.drawPolygonHandler = new L.Draw.Polygon(mapWrapper.map, {
            allowIntersection: false,
            shapeOptions: {
                weight: 2,
                fillOpacity: 0.2,
                color: GREEN,
                fillColor: GREEN
            }
        });
    }

    initMap() {
        this.options = this.options ? this.options : {};
        this.map = L.map(this.$element[0].children[0], {
            zoomControl: false,
            worldCopyJump: true,
            minZoom: 2,
            scrollWheelZoom: !this.options.static,
            doubleClickZoom: !this.options.static,
            dragging: !this.options.static,
            touchZoom: !this.options.static,
            boxZoom: !this.options.static,
            keyboard: !this.options.static,
            tap: !this.options.static,
            maxZoom: 30
        }).setView(
            this.initialCenter ? this.initialCenter : [0, 0],
            this.initialZoom ? this.initialZoom : 2
        ).on('zoom', () => {
            this.zoomLevel = this.map.getZoom();
            this.$scope.$evalAsync();
        });


        this.$timeout(() => {
            this.map.invalidateSize();
            this.mapWrapper = this.mapService.registerMap(this.map, this.mapId, this.options);
        }, 750);

        this.basemapOptions = BUILDCONFIG.BASEMAPS.layers;
        this.basemapKeys = Object.keys(this.basemapOptions);
    }

    zoomIn() {
        this.map.zoomIn();
        this.$timeout(() => {}, 500);
        this.zoomLevel = this.map.getZoom();
    }

    zoomOut() {
        this.map.zoomOut();
        this.$timeout(()=> {}, 500);
        this.zoomLevel = this.map.getZoom();
    }

    toggleFullscreen() {
        this.mapWrapper.toggleFullscreen();
    }

    toggleLayerPicker(event) {
        event.stopPropagation();
        const onClick = () => {
            this.layerPickerOpen = false;
            this.$document.off('click', this.clickListener);
            this.$scope.$evalAsync();
        };
        if (!this.layerPickerOpen) {
            this.layerPickerOpen = true;
            this.clickListener = onClick;
            this.$document.on('click', onClick);
        } else {
            this.layerPickerOpen = false;
            this.$document.off('click', this.clickListener);
            delete this.clickListener;
        }
    }

    toggleableLayers() {
        if (this.mapWrapper) {
            // eslint-disable-next-line
            return Array.from(this.mapWrapper._toggleableLayers.values());
        }
        return [];
    }


    layerEnabled(layerId) {
        if (this.mapWrapper) {
            return this.mapWrapper.getLayerVisibility(layerId) === 'visible';
        }
        return false;
    }

    toggleLayer(layerId) {
        const layerState = this.mapWrapper.getLayerVisibility(layerId);
        if (layerState === 'visible') {
            this.mapWrapper.hideLayers(layerId);
        } else if (layerState === 'hidden' || layerState === 'mixed') {
            this.mapWrapper.showLayers(layerId);
        }
    }

    toggleGeojson() {
        if (this.mapWrapper.getGeojsonVisibility() === 'hidden') {
            this.mapWrapper.showGeojson();
        } else {
            this.mapWrapper.hideGeojson();
        }
    }

    geojsonEnabled() {
        if (this.mapWrapper) {
            return this.mapWrapper.getGeojsonVisibility() === 'visible';
        }
        return false;
    }

    mapHasGeojson() {
        if (this.mapWrapper) {
            // eslint-disable-next-line
            return this.mapWrapper._geoJsonMap.size > 0;
        }
        return false;
    }

    setBasemap(basemapKey) {
        this.mapWrapper.setBasemap(basemapKey);
    }

    getBasemapStyle(basemapKey) {
        if (this.mapWrapper) {
            let options = this.basemapOptions[basemapKey];
            let url = L.Util.template(
                options.url,
                Object.assign(
                    {
                        s: options.properties.subdomains && options.properties.subdomains[0] ?
                            options.properties.subdomains[0] : 'a',
                        z: '17',
                        x: '38168',
                        y: '49642'
                    },
                    options.properties
                )
            );
            return {'background': `url(${url}) no-repeat center`};
        }
        return {};
    }

    cancelPropagation(event) {
        event.stopPropagation();
    }

    openMapSearchModal() {
        this.modalService
            .open({
                component: 'rfMapSearchModal',
                resolve: { }
            }).result.then(location => {
                if (location.coords) {
                    this.map.setView(location.coords, 11);
                } else {
                    const mapView = location.mapView;
                    this.map.fitBounds([
                        [mapView.bottomRight.latitude, mapView.bottomRight.longitude],
                        [mapView.topLeft.latitude, mapView.topLeft.longitude]
                    ]);
                }
            });
    }

    startDrawingShape() {
        this.shapeInProgress = true;
        this.drawPolygonHandler.enable();
    }

    onCancelDrawing() {
        if (this.shapeInProgress) {
            this.shapeInProgress = false;
            this.drawPolygonHandler.disable();
        } else if (this.editingStage) {
            this.editingStage = null;
            this.cancelEditingShape();
        } else if (this.shapes.features.length) {
            this.shapes.features.map(f => f.id).forEach((id) => {
                this.mapWrapper.deleteGeojson(id);
            });
            this.shapes = {
                type: 'FeatureCollection',
                features: []
            };
        } else {
            this.cancelDrawing();
        }
    }

    onFinishDrawing() {
        this.shapes.features.map(f => f.id).forEach((id) => {
            this.mapWrapper.deleteGeojson(id);
        });
        this.finishDrawing(this.shapes);
        this.shapes = {
            type: 'FeatureCollection',
            features: []
        };
    }

    createShape(event) {
        this.shapeInProgress = false;
        const shapeJson = event.layer.toGeoJSON();
        shapeJson.id = this.uuid4.generate();

        this.shapes.features.push(shapeJson);
        this.addShapeToMap(this.mapWrapper, shapeJson);
        this.$scope.$evalAsync();
    }

    addShapeToMap(mapWrapper, shapeJson) {
        mapWrapper.setGeojson(shapeJson.id, shapeJson, {
            style: () => ({
                fillColor: GREEN,
                color: GREEN,
                fillOpacity: 0.2
            }),
            onEachFeature: (feature, layer) => {
                layer.on({
                    mouseover: (e) => this.onShapeMouseover(e),
                    mouseout: (e) => this.onShapeMouseout(e),
                    click: () => this.onShapeClick(shapeJson.id)
                });
            }
        });
    }

    onEditStart() {
        this.editingStage = 'select';
    }

    onEditFinish() {
        this.editingStage = null;
        let drawLayer = _.first(this.mapWrapper.getLayers('draw'));
        if (drawLayer.transform) {
            drawLayer.transform.disable();
        }

        let {geometry} = drawLayer.toGeoJSON();
        this.finishEditingShape(geometry);
    }

    onEditDelete() {
        this.editingStage = null;
        let drawLayer = _.first(this.mapWrapper.getLayers('draw'));
        if (drawLayer.transform) {
            drawLayer.transform.disable();
        }
        this.finishEditingShape();
    }

    onShapeMouseover(e) {
        if (this.editingStage === 'select') {
            let layer = e.target;
            layer.setStyle({
                fillOpacity: 0.7
            });
        }
    }

    onShapeMouseout(e) {
        e.target.setStyle({
            fillOpacity: 0.2
        });
    }

    onShapeClick(id) {
        if (this.editingStage === 'select') {
            this.editShape(id);
        }
    }

    editShape(id) {
        this.editingStage = 'modify';
        this.$scope.$evalAsync();
        let editIndex = _.findIndex(this.shapes.features, f => f.id === id);

        this.mapWrapper.deleteGeojson(id);

        const editPromise = this.$q((resolve, reject) => {
            let drawLayer = _.first(this.mapWrapper.getLayers('draw'));
            if (!_.isEmpty(drawLayer)
                && drawLayer.transform) {
                drawLayer.transform.disable();
            }
            this.startEditingShape(
                this.shapes.features[editIndex].geometry,
                resolve, reject
            );
        });

        editPromise.then((geojson) => {
            if (geojson) {
                this.shapes.features[editIndex].geometry = geojson;
                this.addShapeToMap(this.mapWrapper, this.shapes.features[editIndex]);
            } else {
                this.shapes.features.splice(editIndex, 1);
            }
        }, () => {
            this.addShapeToMap(this.mapWrapper, this.shapes.features[editIndex]);
        });
    }
}

const MapContainerModule = angular.module('components.map.mapContainer', []);

MapContainerModule.component('rfMapContainer', MapContainerComponent);
MapContainerModule.controller('MapContainerController', MapContainerController);

export default MapContainerModule;

/* globals BUILDCONFIG, mathjs*/
import angular from 'angular';
import turfArea from '@turf/area';
import turfDistance from '@turf/distance';
import labMapTpl from './labMap.html';

require('./frame.module.js');
require('./labMap.scss');
require('leaflet-draw/dist/leaflet.draw.css');
require('leaflet-draw/dist/leaflet.draw.js');

const GREEN = '#81C784';

const LabMapComponent = {
    templateUrl: labMapTpl,
    controller: 'LabMapController',
    bindings: {
        mapId: '@',
        analysisId: '@?',
        options: '<?',
        initialCenter: '<?',
        onClose: '&',
        onCompareClick: '&',
        comparing: '<',
        enableNodeExport: '<?'
    }
};

class LabMapController {
    constructor(
        $rootScope, $log, $document, $element, $scope, $timeout, $compile, $window, $ngRedux,
        mapService, authService, APP_CONFIG
    ) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
        this.getMap = () => this.mapService.getMap(this.mapId);

        $ngRedux.subscribe(() => {
            this.nodes = $ngRedux.getState().lab.nodes;
            this.nodeArray = this.nodes ?
                this.nodes.toArray().filter((node) => {
                    return node.type !== 'const';
                }).map(({id, metadata}) => ({id, label: metadata.label})) :
                [];
        });
    }

    $onInit() {
        this.initMap();
    }

    $onChanges(changes) {
        if (changes.options && changes.options.currentValue) {
            this.getMap().then((mapWrapper) => {
                mapWrapper.changeOptions(changes.options.currentValues);
            });
        }
    }

    $onDestroy() {
        this.mapWrapper.deleteLayers('Measurement');
        this.drawListener.forEach((listener) => this.map.off(listener));
        this.disableDrawHandlers();

        this.mapService.deregisterMap(this.mapId);
        delete this.mapWrapper;
        if (this.clickListener) {
            this.$document.off('click', this.clickListener);
        }
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
            this.setDrawListeners();
            this.setDrawHandlers();
        }, 400);

        this.basemapOptions = BUILDCONFIG.BASEMAPS.layers;
        this.basemapKeys = Object.keys(this.basemapOptions);
    }

    setDrawListeners() {
        this.drawListener = [
            this.mapWrapper.on(L.Draw.Event.CREATED, this.createMeasureShape.bind(this))
        ];
    }

    setDrawHandlers() {
        this.drawPolygonHandler = new L.Draw.Polygon(this.mapWrapper.map, {
            allowIntersection: false,
            shapeOptions: {
                weight: 2,
                fillOpacity: 0.2,
                color: GREEN,
                fillColor: GREEN
            }
        });
        this.drawPolylineHandler = new L.Draw.Polyline(this.mapWrapper.map, {
            shapeOptions: {
                weight: 2,
                color: GREEN,
                fillColor: GREEN
            },
            metric: true,
            feet: false,
            showLength: false
        });
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

    createMeasureShape(e) {
        this.disableDrawHandlers();
        if (e.layerType === 'rectangle') {
            this.drawBboxRectangleHandler.disable();
            this.addExportBboxToMap(e.layer);
        } else {
            this.addMeasureShapeToMap(e.layer, e.layerType);
        }
    }

    disableDrawHandlers() {
        this.drawPolygonHandler.disable();
        this.drawPolylineHandler.disable();
    }

    addMeasureShapeToMap(layer, type) {
        let measurement = this.measureCal(type, layer);
        let compiledPopup = this.setPopupContent(type, measurement, layer);
        let measureLayers = this.mapWrapper.getLayers('Measurement');
        measureLayers.push(layer.bindPopup(compiledPopup[0]));
        this.mapWrapper.setLayer('Measurement', measureLayers, false);
        layer.openPopup();
    }

    measureCal(shapeType, layer) {
        let dataGeojson = layer.toGeoJSON();
        if (shapeType === 'polygon') {
            return mathjs.round(turfArea(dataGeojson), 2).toString();
        } else if (shapeType === 'polyline') {
            let length = 0;
            dataGeojson.geometry.coordinates.forEach((v, i, a) => {
                if (i !== a.length - 1) {
                    length += turfDistance(v, a[i + 1], 'kilometers');
                }
            });
            return mathjs.round(length * 1000, 2).toString();
        }
        return '';
    }

    setPopupContent(shapeType, measurement, layer) {
        let popupScope = this.$scope.$new();
        let popupContent = angular.element(
            `
            <rf-measurement-popup
                delete="deleteMeasureShape()"
                type="type"
                measurement="measurement">
            </rf-measurement-popup>
            `
        );
        popupScope.type = shapeType;
        popupScope.measurement = measurement;
        popupScope.deleteMeasureShape = () => {
            this.removeMeasureLayer(layer);
        };
        return this.$compile(popupContent)(popupScope);
    }

    removeMeasureLayer(layer) {
        let measureLayers = this.mapWrapper.getLayers('Measurement');
        let indexOfLayer = measureLayers.indexOf(layer);
        if (indexOfLayer !== -1) {
            measureLayers.splice(indexOfLayer, 1);
        }
        this.mapWrapper.setLayer('Measurement', measureLayers, false);
    }

    toggleMeasurePicker(event) {
        event.stopPropagation();
        const onClick = () => {
            this.measurePickerOpen = false;
            this.$document.off('click', this.clickListener);
            this.$scope.$evalAsync();
        };
        if (!this.measurePickerOpen) {
            this.measurePickerOpen = true;
            this.clickListener = onClick;
            this.$document.on('click', onClick);
        } else {
            this.measurePickerOpen = false;
            this.$document.off('click', this.clickListener);
            delete this.clickListener;
        }
    }

    toggleQuickExport(event) {
        event.stopPropagation();
        if (this.quickExportOpen) {
            this.onExportCancel();
        } else {
            this.quickExportOpen = true;
            this.setBboxDrawHandlers();
            this.drawBboxRectangleHandler.enable();
        }
    }

    setBboxDrawHandlers() {
        this.drawBboxRectangleHandler = new L.Draw.Rectangle(this.mapWrapper.map, {
            allowIntersection: false,
            shapeOptions: {
                weight: 2,
                fillOpacity: 0.2,
                color: GREEN,
                fillColor: GREEN
            }
        });
    }

    addExportBboxToMap(layer) {
        this.exportBboxString = layer.getBounds().toBBoxString();
        this.mapWrapper.setLayer('Export', layer, false);
        this.hasExportBbox = true;
        this.$scope.$evalAsync();
    }

    onExportCancel() {
        this.hasExportBbox = false;
        this.quickExportOpen = false;
        this.exportConfirmed = false;
        delete this.selectedNode;
        delete this.exportBboxString;
        this.drawBboxRectangleHandler.disable();
        this.mapWrapper.deleteLayers('Export');
    }

    onExportConfirm() {
        this.exportConfirmed = true;
        this.downLoadUrl = `${this.APP_CONFIG.tileServerLocation}/tools/${this.analysisId}/raw`
            + `?bbox=${this.exportBboxString}&zoom=${this.map.getZoom()}`
            + `&node=${this.selectedNode.id}&token=${this.authService.token()}`;
    }

    onExportDownload() {
        this.exportConfirmed = false;
        this.$window.open(this.downLoadUrl, '_blank');
    }

    toggleMeasure(shapeType) {
        if (shapeType === 'Polygon') {
            this.drawPolygonHandler.enable();
            this.drawPolylineHandler.disable();
        } else if (shapeType === 'Polyline') {
            this.drawPolylineHandler.enable();
            this.drawPolygonHandler.disable();
        }
    }

    onLabMapClose() {
        this.mapWrapper.deleteLayers('Measurement');
        this.disableDrawHandlers();
        this.onClose();
    }

    onNodeSelect(node) {
        this.selectedNode = node;
    }
}

const LabMapModule = angular.module('components.map.labMap', []);

LabMapModule.component('rfLabMap', LabMapComponent);
LabMapModule.controller('LabMapController', LabMapController);

export default LabMapModule;

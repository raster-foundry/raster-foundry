import tpl from './index.html';
import turfBbox from '@turf/bbox';
import _ from 'lodash';

const GREEN = '#81C784';
const RED = '#E57373';
const AOILAYER = 'Drawn AOI';
const EDITLAYER = 'Edit Layer';

const editShapeOptions = {
    fillColor: RED,
    color: RED,
    opacity: 0.5
};

class AoiDrawToolbarController {
    constructor($rootScope, $scope, $state, $log, mapService) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    $onInit() {
        this.setComponentStyle();
        this.setMapEvents();

        this.selectedFilterName = this.filterList && this.filterList[0].name;
        this.isDrawnAoi = false;
        this.isDrawingAoi = false;
        this.isEditingAoi = false;
        this.aoiLayerName = AOILAYER;
        this.editLayerName = EDITLAYER;

        this.$scope.$watch('$ctrl.layerAoiGeom', this.onLayerAoiGeomChange.bind(this), true);
    }

    onLayerAoiGeomChange(layerAoiGeom) {
        if (layerAoiGeom && layerAoiGeom.type) {
            this.isDrawnAoi = true;
            this.aoiGeojson = {
                geometry: layerAoiGeom,
                properties: {}
            };
            this.setAoi();
        } else {
            this.isDrawnAoi = false;
            delete this.aoiGeojson;
            this.removeDrawnAoi();
            this.removeEditAoi();
        }
    }

    $onDestroy() {
        this.isDrawnAoi = false;
        this.removeMapListeners();
        this.disableDrawHandlers();
        this.removeDrawnAoi();
    }

    isValidGeometry() {
        return _.get(this, 'aoiGeojson.geometry.type');
    }

    setComponentStyle() {
        const navebarClass = '.navbar.light-navbar';
        const height = angular.element(document.querySelector(navebarClass))[0].offsetHeight;
        this.eleStyle = {
            height: `${height}px`
        };
        this.leftStyle = { height: `${height}px` };
    }

    setMapEvents() {
        this.getMap().then(mapWrapper => {
            this.listeners = [mapWrapper.on(L.Draw.Event.CREATED, this.createShape.bind(this))];
            this.setDrawHandlers(mapWrapper);
        });
    }

    setDrawHandlers(mapWrapper) {
        this.drawRectangleHandler = new L.Draw.Rectangle(mapWrapper.map, {
            shapeOptions: this.getShapeOptions(this.layerAoiColor)
        });
        this.drawPolygonHandler = new L.Draw.Polygon(mapWrapper.map, {
            allowIntersection: false,
            shapeOptions: this.getShapeOptions(this.layerAoiColor)
        });
    }

    getMap() {
        return this.mapService.getMap(this.mapId);
    }

    onChangeFilter(id) {
        const selectedFilter = this.filterList.find(fl => fl.id === id);
        if (selectedFilter) {
            this.selectedFilterName = selectedFilter.name;
            this.disableDrawHandlers();
            this.onChangeFilterList({ id });
        }
    }

    onDrawAoi() {
        this.isDrawingAoi = true;
        this.onShapeOp({ isInProgress: true });
        if (this.geomDrawType.toUpperCase() === 'POLYGON') {
            this.drawPolygonHandler.enable();
        }

        if (this.geomDrawType.toUpperCase() === 'RECTANGLE') {
            this.drawRectangleHandler.enable();
        }
    }

    disableDrawHandlers() {
        if (this.drawPolygonHandler && this.drawRectangleHandler) {
            this.drawPolygonHandler.disable();
            this.drawRectangleHandler.disable();
        }
    }

    createShape(e) {
        this.isDrawnAoi = true;
        this.isDrawingAoi = false;
        this.onShapeOp({ isInProgress: false });
        this.aoiGeojson = e.layer.toGeoJSON();
        this.setAoi();
    }

    setAoi() {
        const geom = this.aoiGeojson.geometry;
        if (geom) {
            this.showAoi(geom);
        }
    }

    showAoi(aoiPolygonGeojson) {
        this.getMap().then(mapWrapper => {
            mapWrapper.setGeojson(this.aoiLayerName, aoiPolygonGeojson, {});
            const bbox = turfBbox(aoiPolygonGeojson);
            const bounds = L.latLngBounds(L.latLng(bbox[1], bbox[0]), L.latLng(bbox[3], bbox[2]));
            mapWrapper.map.fitBounds(bounds);
        });
    }

    onClickCancel() {
        this.onShapeOp({ isInProgress: false });
        this.isEditingAoi = false;
        this.disableEditHandler();
        this.removeEditAoi();
        this.disableDrawHandlers();
        this.onCancel();
    }

    onClickConfirmAOI(isSaveShape) {
        this.onConfirmAoi({
            aoiGeojson: this.aoiGeojson,
            isSaveShape
        });
    }

    removeDrawnAoi() {
        this.getMap().then(mapWrapper => {
            mapWrapper.deleteGeojson(this.aoiLayerName);
        });
    }

    removeMapListeners() {
        this.getMap().then(mapWrapper => {
            this.listeners.forEach(listener => {
                mapWrapper.off(listener);
            });
        });
    }

    onClickDeleteAoi() {
        this.isDrawnAoi = false;
        this.removeDrawnAoi();
        this.onShapeOp({ isInProgress: false });
        this.aoiGeojson = {};
    }

    onClickEditAoi() {
        const geom = _.get(this, 'aoiGeojson.geometry');
        if (geom) {
            let coordinates = [];
            const geomType = geom.type;

            this.isEditingAoi = true;
            this.onShapeOp({ isInProgress: this.isEditingAoi });

            if (geomType && geomType.toUpperCase() === 'MULTIPOLYGON') {
                coordinates = geom.coordinates[0][0].map(c => [c[1], c[0]]);
            } else {
                coordinates = geom.coordinates[0].map(c => [c[1], c[0]]);
            }

            this.removeDrawnAoi();
            this.editLayer = L.polygon(coordinates, editShapeOptions);
            this.getMap().then(mapWrapper => {
                this.editHandler = new L.EditToolbar.Edit(mapWrapper.map, {
                    featureGroup: L.featureGroup([this.editLayer])
                });
                mapWrapper.setLayer(this.editLayerName, this.editLayer);
                this.editHandler.enable();
            });
        }
    }

    onClickCancelEdit() {
        this.clearEditState();
    }

    onClickConfirmEdit() {
        this.aoiGeojson = this.editLayer.toGeoJSON();
        this.isDrawnAoi = true;
        this.clearEditState();
    }

    clearEditState() {
        this.isEditingAoi = false;
        this.onShapeOp({ isInProgress: false });
        this.disableEditHandler();
        this.removeEditAoi();
        this.setAoi();
        delete this.editLayer;
    }

    disableEditHandler() {
        if (this.editHandler) {
            this.editHandler.disable();
            delete this.editHandler;
        }
    }

    removeEditAoi() {
        this.getMap().then(mapWrapper => {
            mapWrapper.deleteLayers(this.editLayerName);
        });
    }

    getShapeOptions(color) {
        return {
            weight: 2,
            fillOpacity: 0.2,
            color: color || GREEN,
            fillColor: color || GREEN
        };
    }
}

const component = {
    bindings: {
        mapId: '<',
        layerAoiGeom: '<?',
        layerAoiColor: '<?',
        geomDrawType: '<',
        filterList: '<?',
        selectedGeom: '<?',
        onConfirmAoi: '&',
        onCancel: '&',
        onChangeFilterList: '&?',
        onShapeOp: '&?'
    },
    templateUrl: tpl,
    controller: AoiDrawToolbarController.name
};

export default angular
    .module('components.projects.aoiDrawToolbar', [])
    .controller(AoiDrawToolbarController.name, AoiDrawToolbarController)
    .component('rfAoiDrawToolbar', component).name;

import tpl from './index.html';

const GREEN = '#81C784';
const RED = '#E57373';
const AOILAYER = 'Drawn AOI';
const EDITLAYER = 'Edit Layer';

const drawShapeOptions = {
    weight: 2,
    fillOpacity: 0.2,
    color: GREEN,
    fillColor: GREEN
};

const editShapeOptions = {
    fillColor: RED,
    color: RED,
    opacity: 0.5
};

class AoiDrawToolbarController {
    constructor(
        $rootScope, $scope, $state, $log,
        mapService
    ) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    $onInit() {
        this.setComponentStyle();
        this.setMapEvents();

        this.isDrawnAoi = false;
        this.isDrawingAoi = false;
        this.isEditingAoi = false;
        this.aoiLayerName = AOILAYER;
        this.editLayerName = EDITLAYER;
    }

    $onChanges(changes) {
        if (changes.layerAoi && changes.layerAoi.currentValue) {
            this.layerAoi = changes.layerAoi.currentValue;
            this.setAoi();
        }
    }

    $onDestroy() {
        this.isDrawnAoi = false;
        this.removeMapListeners();
        this.disableDrawHandlers();
        this.removeDrawnAoi();
    }

    setComponentStyle() {
        const navebarClass = '.navbar.light-navbar';
        const height = angular.element(document.querySelector(navebarClass))[0].offsetHeight;
        this.eleStyle = {
            top: `${-height}px`,
            height: `${height}px`
        };
        this.leftStyle = {height: `${height}px`};
    }

    setMapEvents() {
        this.getMap().then((mapWrapper) => {
            this.listeners = [
                mapWrapper.on(L.Draw.Event.CREATED, this.createShape.bind(this))
            ];
            this.setDrawHandlers(mapWrapper);
        });
    }

    setAoi() {
        this.$log.log('layerAoi', this.layerAoi);
        if (this.layerAoi) {
            this.$log.log(this.layerAoi);
            this.showAoi(this.layerAoi);
        }
    }

    setDrawHandlers(mapWrapper) {
        this.drawRectangleHandler = new L.Draw.Rectangle(mapWrapper.map, {
            shapeOptions: drawShapeOptions
        });
        this.drawPolygonHandler = new L.Draw.Polygon(mapWrapper.map, {
            allowIntersection: false,
            shapeOptions: drawShapeOptions
        });
    }

    getMap() {
        return this.mapService.getMap(this.mapId);
    }

    onChangeFilter(id) {
        this.disableDrawHandlers();
        this.onChangeFilterList({id});
    }

    onDrawAoi() {
        this.isDrawingAoi = true;
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
        this.aoiGeojson = e.layer.toGeoJSON();
        this.layerAoi = this.aoiGeojson.geometry;
        this.setAoi();
    }

    showAoi(aoiGeojson) {
        this.getMap().then(mapWrapper => {
            mapWrapper.setGeojson(this.aoiLayerName, aoiGeojson, {});
        });
    }

    onClickCancel() {
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
        this.getMap().then((mapWrapper) => {
            mapWrapper.deleteGeojson(this.aoiLayerName);
        });
    }

    removeMapListeners() {
        this.getMap().then((mapWrapper) => {
            this.listeners.forEach((listener) => {
                mapWrapper.off(listener);
            });
        });
    }

    onClickDeleteAoi() {
        this.isDrawnAoi = false;
        this.removeDrawnAoi();
        delete this.layerAoi;
    }

    onClickEditAoi() {
        if (this.layerAoi) {
            this.isEditingAoi = true;
            this.editLayer = L.polygon(
                this.layerAoi.coordinates[0].map(c => [c[1], c[0]]), editShapeOptions);
            this.removeDrawnAoi();
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
        this.isEditingAoi = false;
        this.disableEditHandler();
        this.removeEditAoi();
        this.setAoi();
        delete this.editLayer;
    }

    onClickConfirmEdit() {
        this.aoiGeojson = this.editLayer.toGeoJSON();
        this.layerAoi = this.aoiGeojson.geometry;
        this.isEditingAoi = false;
        this.isDrawnAoi = true;
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
}

const component = {
    bindings: {
        mapId: '<',
        layerAoi: '<?',
        geomDrawType: '<',
        filterList: '<?',
        selectedGeom: '<?',
        onConfirmAoi: '&',
        onCancel: '&',
        onChangeFilterList: '&?'
    },
    templateUrl: tpl,
    controller: AoiDrawToolbarController.name
};

export default angular
    .module('components.projects.aoiDrawToolbar', [])
    .controller(AoiDrawToolbarController.name, AoiDrawToolbarController)
    .component('rfAoiDrawToolbar', component)
    .name;

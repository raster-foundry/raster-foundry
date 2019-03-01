import tpl from './index.html';

const mapName = 'project';
const mapLayerName = 'Project Layer';
const shapeLayerName = 'Export AOI';
const geomDrawType = 'Polygon';

class LayerExportCreateController {
    constructor(
        $rootScope,
        $scope,
        $state,
        $log,
        projectService,
        mapService,
        exportService,
        authService,
        modalService
    ) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    $onInit() {
        this.initExportTarget();
        this.initResolutions();
        this.setMapLayers();
        this.initAoiDrawToolbar();
    }

    $onDestroy() {
        this.removeMapLayers();
        this.removeExportAois();
    }

    initAoiDrawToolbar() {
        this.mapId = mapName;
        this.geomDrawType = geomDrawType;
        this.isDrawAoiClicked = false;
    }

    initExportTarget() {
        this.availableTargets = this.exportService.getAvailableTargets();
        const defaultTarget = this.availableTargets.find(target => target.default);
        if (defaultTarget) {
            this.exportTarget = defaultTarget.value;
        }
    }

    initResolutions() {
        this.availableResolutions = this.exportService.getAvailableResolutions();
        const defaultResolution = this.availableResolutions[0];
        if (defaultResolution) {
            this.resolution = defaultResolution.value.toString();
        }
    }

    getMap() {
        return this.mapService.getMap(mapName);
    }

    setMapLayers() {
        let mapLayer = this.projectService.mapLayerFromLayer(this.project, this.layer);
        return this.getMap().then(map => {
            map.setLayer(mapLayerName, mapLayer, true);
        });
    }

    removeMapLayers() {
        this.getMap().then(map => {
            map.deleteLayers(mapLayerName);
        });
    }

    removeExportAois() {
        this.getMap().then(map => {
            map.deleteGeojson(shapeLayerName);
        });
    }

    onCancelDrawAoi() {
        this.$log.log('cancel');
        this.isDrawAoiClicked = false;
    }

    onConfirmAoi(aoiGeojson, isSaveShape) {
        this.$log.log('confirm', aoiGeojson, isSaveShape);
    }

    onClickDefineAoi() {
        this.isDrawAoiClicked = true;
    }

    isValidExportDef() {
        const isValidTarget = this.exportTarget.value;
        const isValidResolution = this.resolution;
        // const isValidGeom = this.
        return false;
    }

    onShapeOp(isInProgress) {
        this.isDrawing = isInProgress;
    }
}

const component = {
    bindings: {
        project: '<',
        layer: '<'
    },
    templateUrl: tpl,
    controller: LayerExportCreateController.name
};

export default angular
    .module('components.pages.project.layer.export', [])
    .controller(LayerExportCreateController.name, LayerExportCreateController)
    .component('rfProjectLayerExportPage', component).name;

import tpl from './index.html';
import _ from 'lodash';

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
        moment,
        projectService,
        mapService,
        exportService,
        authService,
        modalService,
        shapesService
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
        const mapLayer = this.projectService.mapLayerFromLayer(this.project, this.layer);
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
        this.isDrawAoiClicked = false;
    }

    onConfirmAoi(aoiGeojson, isSaveShape) {
        this.isDrawAoiClicked = false;
        const geom = this.getMultiPolygon(aoiGeojson);
        if (geom) {
            this.mask = geom;
            if (isSaveShape) {
                this.openShapeModal(geom);
            }
        } else {
            this.$window('The supplied geometry is incorrect, please try again.');
        }
    }

    getMultiPolygon(aoiGeojson) {
        const geomType = _.get(aoiGeojson, 'geometry.type');
        if (geomType && geomType.toUpperCase() === 'POLYGON') {
            return {
                type: 'MultiPolygon',
                coordinates: [aoiGeojson.geometry.coordinates]
            };
        } else if (geomType && geomType.toUpperCase() === 'MULTIPOLYGON') {
            return aoiGeojson.geometry;
        }
        return 0;
    }

    onClickDefineAoi() {
        this.isDrawAoiClicked = true;
    }

    isValidExportDef() {
        const isValidTarget = _.get(this, 'exportTarget.value');
        const isValidResolution = _.get(this, 'resolution');
        const maskType = _.get(this, 'mask.type');
        const isValidMask = maskType ? maskType.toUpperCase() === 'FEATURECOLLECTION' : false;

        return isValidTarget && isValidResolution && isValidMask;
    }

    onShapeOp(isInProgress) {
        this.isDrawing = isInProgress;
    }

    openShapeModal(geom) {
        const modal = this.modalService.open({
            component: 'rfEnterTokenModal',
            resolve: {
                title: () => 'Enter a name for the vector data',
                token: () =>
                    `${this.project.name} - ${this.layer.name} export AOI ` +
                    `- ${this.formatDateDisplay(this.layer.createdAt)}`
            }
        });
        modal.result
            .then(name => {
                const geomFC = this.shapesService.generateFeatureCollection([geom], name);
                this.shapesService
                    .createShape(geomFC)
                    .then(() => {})
                    .catch(err => {
                        this.$window.alert(
                            'There was an error adding this layer\'s ' +
                                ' export AOI as vector data. Please try again later'
                        );
                        this.$log.error(err);
                    });
            })
            .catch(() => {});
    }

    formatDateDisplay(date) {
        return date.length ? this.moment.utc(date).format('LLL') + ' (UTC)' : 'MM/DD/YYYY';
    }

    getExportSettings() {
        // TODO: get exportType
        return {
            exportType: '',
            projectLayerId: this.layer.id
        };
    }

    getExportOptions() {
        // TODO: get bands
        return {
            resolution: parseInt(this.resolution, 10),
            raw: false,
            mask: this.mask,
            bands: []
        };
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

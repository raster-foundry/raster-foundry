/* globals BUILDCONFIG */
import tpl from './index.html';
import { min, range, get } from 'lodash';

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
        $timeout,
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
        this.initMask();
        this.getDefaultBands();
        this.setMapLayers();
        this.initAoiDrawToolbar();

        this.isCreatingExport = false;
    }

    $onDestroy() {
        this.removeMapLayers();
        this.removeExportAois();
    }

    initExportTarget() {
        this.availableTargets = this.exportService.getAvailableTargets();
        const defaultTarget = this.availableTargets.find(target => target.default);
        this.exportTarget = defaultTarget.value || this.availableTargets[0];
        this.exportSource = '';
        this.exportType = 'S3';
    }

    initResolutions() {
        this.availableResolutions = this.exportService.getAvailableResolutions();
        const defaultRes = this.availableResolutions[0];
        const defaultVal = defaultRes.value;
        this.resolution = defaultVal ? defaultVal.toString() : '9';
    }

    initMask() {
        this.mask = {};
    }

    getDefaultBands() {
        this.projectService.getProjectDatasources(this.project.id).then(datasources => {
            let minBands = min(
                datasources.map(datasource => {
                    return datasource.bands.length;
                })
            );
            this.defaultBands = range(0, minBands);
        });
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

    initAoiDrawToolbar() {
        this.mapId = mapName;
        this.geomDrawType = geomDrawType;
        this.isDrawAoiClicked = false;
        this.layerGeom = {};
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
        if (this.mask.type) {
            this.displayExportAoi();
        }
    }

    onConfirmAoi(aoiGeojson, isSaveShape) {
        this.isDrawAoiClicked = false;
        const geom = this.getMultiPolygon(aoiGeojson);
        if (geom) {
            this.mask = geom;
            this.displayExportAoi();
            if (isSaveShape) {
                this.openShapeModal(geom);
            }
        } else {
            this.$window('The supplied geometry is incorrect, please try again.');
        }
    }

    displayExportAoi() {
        this.getMap().then(map => {
            map.setGeojson(shapeLayerName, this.mask);
        });
    }

    getMultiPolygon(aoiGeojson) {
        const geomType = get(aoiGeojson, 'geometry.type');
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
        this.layerGeom = {};
        this.removeExportAois();
        if (this.mask.type) {
            this.layerGeom = this.mask;
        }
    }

    isValidExportDef() {
        const isValidType = this.exportType === 'S3' || this.exportType === 'Dropbox';
        const isValidResolution = this.availableResolutions
            .map(res => res.value)
            .includes(parseInt(this.resolution, 10));
        const maskType = get(this, 'mask.type');
        const isValidMask = maskType ? maskType.toUpperCase() === 'MULTIPOLYGON' : false;
        const isValidBands = !!get(this, 'defaultBands.length');

        let result = isValidType && isValidResolution && isValidMask && isValidBands;

        if (this.exportTarget !== 'internalS3') {
            result = result && get(this, 'exportSource.length');
        }

        return result;
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
        return {
            exportType: this.exportType,
            projectLayerId: this.layer.id
        };
    }

    getExportOptions() {
        let result = {
            resolution: parseInt(this.resolution, 10),
            raw: false,
            mask: this.mask,
            bands: this.defaultBands
        };

        if (this.exportTarget !== 'internalS3') {
            result.source = this.exportSource;
        }

        return result;
    }

    onExportTargetChange() {
        this.exportSource = '';
        this.exportType = 'S3';
        if (this.exportTarget === 'dropbox') {
            this.exportType = 'Dropbox';
            const hasDropbox =
                this.authService.user.dropboxCredential &&
                this.authService.user.dropboxCredential.length;
            if (hasDropbox) {
                let appName = BUILDCONFIG.APP_NAME.toLowerCase().replace(' ', '-');
                this.exportSource = `dropbox:///${appName}/projects/${this.project.id}/layer/${
                    this.layer.id
                }`;
            } else {
                this.displayDropboxModal();
            }
        }
    }

    displayDropboxModal() {
        this.modalService
            .open({
                component: 'rfConfirmationModal',
                resolve: {
                    title: () => 'You don\'t have Dropbox credential set',
                    content: () => 'Go to your API connections page and set one?',
                    confirmText: () => 'Add Dropbox credential',
                    cancelText: () => 'Cancel'
                }
            })
            .result.then(resp => {
                this.$state.go('user.settings.connections', { userId: 'me' });
            })
            .catch(() => {});
    }

    onClickCreateExport() {
        this.isCreatingExport = true;
        this.projectService
            .export(this.project, this.getExportSettings(), this.getExportOptions())
            .then(() => {
                this.finishExport();
            })
            .catch(err => {
                this.isCreatingExport = false;
                this.displayErrorModal();
                this.$log.log(err);
            });
    }

    displayErrorModal() {
        const modal = this.modalService.open({
            component: 'rfFeedbackModal',
            resolve: {
                title: () => 'Error',
                subtitle: () => 'There was an error creating this export',
                content: () =>
                    '<h2>Error</h2>' + '<p>There was an error when creating this export.</p>',
                feedbackIconType: () => 'danger',
                feedbackIcon: () => 'icon-warning',
                feedbackBtnType: () => 'btn-danger',
                feedbackBtnText: () => 'Retry?',
                cancelText: () => 'Cancel'
            }
        });
        modal.result.then(() => {
            this.initExportTarget();
            this.initResolutions();
            this.initMask();
        });
    }

    finishExport() {
        this.$timeout(() => {
            this.isCreatingExport = false;
            this.$state.go('project.layer.exports', {
                projectId: this.project.id,
                layerId: this.layer.id
            });
        }, 500);
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

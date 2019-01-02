/* global L */
import angular from 'angular';
import sceneDetailModalTpl from './sceneDetailModal.html';
require('./sceneDetailModal.scss');

const SceneDetailModalComponent = {
    templateUrl: sceneDetailModalTpl,
    bindings: {
        close: '&',
        dismiss: '&',
        modalInstance: '<',
        resolve: '<'
    },
    controller: 'SceneDetailModalController'
};

class SceneDetailModalController {
    constructor(
        $log, $state, modalService, $scope, $rootScope,
        moment, sceneService, mapService,
        authService
    ) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    $onInit() {
        this.$scope.$on('$destroy', () => {
            this.mapService.deregisterMap('scene-preview-map');
        });
        this.scene = this.resolve.scene;
        this.repository = this.resolve.repository;
    }

    $postLink() {
        this.setThumbnail();
        this.repository.service.getDatasource(this.scene).then(d => {
            this.datasource = d;
        });
        this.thumbnail = false;
        this.repository.service.getThumbnail(this.scene).then(t => {
            this.thumbnail = t;
        });
        this.accDateDisplay = this.setAccDateDisplay();
        this.isUploadDone = true;
        this.isOwner = this.scene.owner === this.authService.getProfile().sub;
    }

    setThumbnail() {
        this.getMap().then(mapWrapper => {
            if (this.scene.sceneType === 'COG') {
                this.setCOGThumbnail(mapWrapper);
            } else {
                mapWrapper.setThumbnail(this.scene, this.repository, {persist: true});
            }
            mapWrapper.map.fitBounds(this.getSceneBounds());
        });
    }

    setCOGThumbnail(mapWrapper) {
        this.repository.service.getDatasourceBands(this.scene).then(rgbBands => {
            mapWrapper.setLayer(
              'Browse Scene',
              L.tileLayer(
                this.sceneService.getSceneLayerURL(
                    this.scene,
                    {
                        token: this.authService.token(),
                        redBand: rgbBands.RED,
                        greenBand: rgbBands.GREEN,
                        blueBand: rgbBands.BLUE
                    }
                ),
                {maxZoom: 30}
              ),
              true
            );
        });
    }

    getMap() {
        return this.mapService.getMap('scene-preview-map');
    }

    openDownloadModal() {
        this.modalService.open({
            component: 'rfSceneDownloadModal',
            resolve: {
                scene: () => this.scene
            }
        }).result.catch(() => {});
    }

    getSceneBounds() {
        const bounds = L.geoJSON(this.scene.dataFootprint).getBounds();
        return bounds;
    }

    closeWithData(data) {
        this.close({$value: data});
    }

    cancelEditing() {
        this.editingMetadata = false;
    }

    startEditing() {
        if (!this.sources) {
            this.repository.service.getSources().then((sources) => {
                this.sources = sources;
                this.selectedDatasource = this.datasource;
                this.editingMetadata = true;
            }, (err) => {
                this.selectedDatasource = this.datasource;
                this.datasourceError = err;
            });
        } else {
            this.selectedDatasource = this.datasource;
            this.editingMetadata = true;
        }
    }

    finishEditing() {
        this.editingMetadata = false;
        this.updateMetadata();
    }

    setAccDateDisplay() {
        return this.scene.filterFields && this.scene.filterFields.acquisitionDate ?
            this.formatAcqDate(this.scene.filterFields.acquisitionDate) :
            'MM/DD/YYYY';
    }

    updateMetadata() {
        // TODO: visibility should be editable eventually
        this.isUploadDone = false;
        if (!this.newFilterFields.acquisitionDate) {
            this.newFilterFields.acquisitionDate = this.scene.filterFields.acquisitionDate;
        }
        this.scene = Object.assign(this.scene, {
            datasource: this.selectedDatasource.id,
            'modifiedAt': this.moment().toISOString(),
            'modifiedBy': this.scene.owner,
            'sceneMetadata': this.newSceneMetadata,
            'filterFields': this.newFilterFields
        });
        this.datasource = this.selectedDatasource;
        this.sceneService.update(this.scene).then(
            () => {
                this.isUploadDone = true;
            },
            () => {
                this.isUploadDone = false;
            }
        );
    }

    formatAcqDate(date) {
        return date.length ? this.moment.utc(date).format('MM/DD/YYYY') + ' (UTC)' : 'MM/DD/YYYY';
    }

    openDatePickerModal(date) {
        this.modalService.open({
            component: 'rfDatePickerModal',
            windowClass: 'auto-width-modal',
            resolve: {
                config: () => Object({
                    selectedDay: this.moment(date)
                })
            }
        }, false).result.then(selectedDay => {
            this.updateAcquisitionDate(selectedDay);
        }).catch(() => {});
    }

    updateAcquisitionDate(selectedDay) {
        if (selectedDay) {
            this.newFilterFields.acquisitionDate = selectedDay.toISOString();
            this.accDateDisplay = selectedDay.format('MM/DD/YYYY');
        }
    }

    getMaxBound(field) {
        switch (field) {
        case 'cloudCover': return 100;
        case 'sunAzimuth': return 360;
        case 'sunElevation': return 180;
        default: throw new Error(`Tried to fetch max bound for invalid field: ${field}`);
        }
    }

    onFilterValChange(field) {
        if (this.newFilterFields[field] < 0) {
            this.newFilterFields[field] = 0;
        } else if (this.newFilterFields[field] > this.getMaxBound(field)) {
            this.newFilterFields[field] = this.getMaxBound(field);
        }
    }

    selectDatasource(item) {
        this.selectedDatasource = item;
    }

    saveDatasourceEdit() {
        this.scene = Object.assign(this.scene, {
            datasource: this.selectedDatasource.id
        });
        this.sceneService.update(this.scene).then(
            () => {
                this.isUploadDone = true;
            },
            () => {
                this.isUploadDone = false;
            }
        );
    }
}
const SceneDetailModalModule = angular.module('components.scenes.sceneDetailModal', []);

SceneDetailModalModule.controller('SceneDetailModalController', SceneDetailModalController);
SceneDetailModalModule.component('rfSceneDetailModal', SceneDetailModalComponent);

export default SceneDetailModalModule;

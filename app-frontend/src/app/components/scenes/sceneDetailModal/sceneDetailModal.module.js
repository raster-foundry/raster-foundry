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
        $log, $state, modalService, $scope,
        moment, sceneService, datasourceService, mapService,
        authService
    ) {
        'ngInject';
        this.$log = $log;
        this.$state = $state;
        this.modalService = modalService;
        this.$scope = $scope;
        this.Moment = moment;
        this.sceneService = sceneService;
        this.datasourceService = datasourceService;
        this.authService = authService;
        this.scene = this.resolve.scene;
        this.getMap = () => mapService.getMap('scene-preview-map');
        $scope.$on('$destroy', () => {
            mapService.deregisterMap('scene-preview-map');
        });
    }

    $postLink() {
        this.datasourceLoaded = false;
        this.getMap().then(mapWrapper => {
            mapWrapper.setThumbnail(this.scene, false, true);
            mapWrapper.map.fitBounds(this.getSceneBounds());
        });
        this.datasourceService.get(this.scene.dataSource).then(d => {
            this.datasourceLoaded = true;
            this.datasource = d;
        });
        this.accDateDisplay = this.setAccDateDisplay();
        this.isUploadDone = true;
    }

    openDownloadModal() {
        const images = this.scene.images.map(i => Object({
            filename: i.filename,
            uri: i.sourceUri,
            metadata: i.metadataFiles || []
        }));

        const downloadSets = [{
            label: this.scene.name,
            metadata: this.scene.metadataFiles || [],
            images: images
        }];

        this.modalService.open({
            component: 'rfSceneDownloadModal',
            resolve: {
                downloads: () => downloadSets
            }
        });
    }

    getSceneBounds() {
        const bounds = L.geoJSON(this.scene.dataFootprint).getBounds();
        return bounds;
    }

    closeWithData(data) {
        this.close({$value: data});
    }

    toggleMetadataEdit() {
        this.isEditMetadata = !this.isEditMetadata;
        if (!this.isEditMetadata) {
            this.updateMetadata();
        }
    }

    setAccDateDisplay() {
        return this.scene.filterFields && this.scene.filterFields.acquisitionDate ?
            this.formatAcqDate(this.scene.filterFields.acquisitionDate) :
            'MM/DD/YYYY';
    }

    updateMetadata() {
        // TODO: visibility and data source should be editable eventually
        this.isUploadDone = false;
        if (!this.newFilterFields.acquisitionDate) {
            this.newFilterFields.acquisitionDate = this.scene.filterFields.acquisitionDate;
        }
        this.scene = Object.assign(this.scene, {
            'modifiedAt': this.Moment().toISOString(),
            'modifiedBy': this.scene.owner,
            'sceneMetadata': this.newSceneMetadata,
            'filterFields': this.newFilterFields
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

    formatAcqDate(date) {
        return date.length ? this.Moment(date).format('MM/DD/YYYY') : 'MM/DD/YYYY';
    }

    openDatePickerModal(date) {
        this.modalService
            .open({
                component: 'rfDatePickerModal',
                windowClass: 'auto-width-modal',
                resolve: {
                    config: () => Object({
                        selectedDay: this.Moment(date)
                    })
                }
            }).result.then(selectedDay => {
                this.updateAcquisitionDate(selectedDay);
            });
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
}
const SceneDetailModalModule = angular.module('components.scenes.sceneDetailModal', []);

SceneDetailModalModule.controller('SceneDetailModalController', SceneDetailModalController);
SceneDetailModalModule.component('rfSceneDetailModal', SceneDetailModalComponent);

export default SceneDetailModalModule;

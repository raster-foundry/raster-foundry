import angular from 'angular';
import sceneTpl from './sceneItem.html';

const SceneItemComponent = {
    templateUrl: sceneTpl,
    transclude: true,
    controller: 'SceneItemController',
    bindings: {
        scene: '<',
        actions: '<',
        selected: '<?',
        onSelect: '&?',
        isDisabled: '<?',
        repository: '<'
    }
};

class SceneItemController {
    constructor(
        $scope, $attrs,
        thumbnailService, mapService, datasourceService, modalService
    ) {
        'ngInject';

        this.$scope = $scope;
        this.$parent = $scope.$parent.$ctrl;

        this.isDraggable = $attrs.hasOwnProperty('draggable');

        this.thumbnailService = thumbnailService;
        this.mapService = mapService;
        this.isDraggable = $attrs.hasOwnProperty('draggable');
        this.isPreviewable = $attrs.hasOwnProperty('previewable');
        this.isClickable = $attrs.hasOwnProperty('clickable');
        this.datasourceService = datasourceService;
        this.modalService = modalService;
        this.$scope = $scope;
    }

    $onInit() {
        this.$scope.$watch('$ctrl.scene.datasource', () => {
            if (this.repository) {
                this.repository.service.getDatasource(this.scene).then((datasource) => {
                    this.datasource = datasource;
                });
            }
        });
    }

    $onChanges(changes) {
        if (changes.selected && changes.selected.hasOwnProperty('currentValue')) {
            this.selectedStatus = changes.selected.currentValue;
        }
        if (changes.repository && changes.repository.currentValue) {
            this.repository = changes.repository.currentValue;
            this.repository.service.getDatasource(this.scene).then((datasource) => {
                this.datasource = datasource;
            });
            this.repository.service.getThumbnail(this.scene).then((thumbnail) => {
                this.thumbnail = thumbnail;
            });
        }
    }

    toggleSelected(event) {
        this.selectedStatus = !this.selectedStatus;
        if (this.onSelect) {
            this.onSelect({scene: this.scene, selected: this.selectedStatus});
            if (event) {
                event.stopPropagation();
            }
        }
    }

    getReferenceDate() {
        if (!this.scene) {
            return '';
        }
        let acqDate = this.scene.filterFields.acquisitionDate;
        return acqDate ? acqDate : this.scene.createdAt;
    }

    getSceneIngestStatus() {
        if (this.scene) {
            if (this.scene.sceneType === 'COG') {
                return 'COG';
            }
            return this.scene.statusFields.ingestStatus;
        }
        return false;
    }

    hasDownloadPermission() {
        if (this.repository.service.getScenePermissions(this.scene).includes('download')) {
            return true;
        }
        return false;
    }

    openSceneDetailModal(e) {
        e.stopPropagation();
        this.modalService.open({
            component: 'rfSceneDetailModal',
            resolve: {
                scene: () => this.scene,
                repository: () => this.repository
            }
        });
    }
}

const SceneItemModule = angular.module('components.scenes.sceneItem', []);

SceneItemModule.component('rfSceneItem', SceneItemComponent);
SceneItemModule.controller('SceneItemController', SceneItemController);

export default SceneItemModule;

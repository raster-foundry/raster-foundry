import angular from 'angular';
import _ from 'lodash';
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
        $scope, $attrs, $element, $timeout,
        thumbnailService, mapService, modalService, sceneService, authService
    ) {
        'ngInject';

        this.$scope = $scope;
        this.$parent = $scope.$parent.$ctrl;
        this.$element = $element;
        this.$timeout = $timeout;

        this.isDraggable = $attrs.hasOwnProperty('draggable');

        this.thumbnailService = thumbnailService;
        this.mapService = mapService;
        this.modalService = modalService;
        this.sceneService = sceneService;
        this.authService = authService;

        this.isDraggable = $attrs.hasOwnProperty('draggable');
        this.isPreviewable = $attrs.hasOwnProperty('previewable');
        this.isClickable = $attrs.hasOwnProperty('clickable');
        this.datasource = this.scene.datasource;
    }

    $postLink() {
        this.$timeout(() => {
            const el = $(this.$element[0]).find('img.item-img').get(0);
            $(el).on('error', () => {
                this.imageError = true;
                this.$scope.$evalAsync();
            });
        }, 0);
    }

    $onChanges(changes) {
        if (changes.selected && changes.selected.hasOwnProperty('currentValue')) {
            this.selectedStatus = changes.selected.currentValue;
        }
        if (changes.repository && changes.repository.currentValue) {
            this.repository = changes.repository.currentValue;
            if (this.scene.sceneType === 'COG') {
                this.sceneService.cogThumbnail(
                    this.scene.id,
                    this.authService.token()
                ).then(res => {
                    let base64String = Object.values(res)
                        .filter(chr => _.isString(chr))
                        .join('');
                    this.thumbnail = `data:image/png;base64,${base64String}`;
                });
            } else {
                this.repository.service.getThumbnail(this.scene).then((thumbnail) => {
                    this.thumbnail = thumbnail;
                });
            }
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

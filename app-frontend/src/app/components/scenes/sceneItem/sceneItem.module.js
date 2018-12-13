import _ from 'lodash';
import angular from 'angular';
import sceneTpl from './sceneItem.html';
import $ from 'jquery';

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
        repository: '<',
        onMove: '&?'
    }
};

class SceneItemController {
    constructor(
        $rootScope, $scope, $attrs, $element, $timeout, $document,
        thumbnailService, mapService, modalService, sceneService, authService
    ) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
        this.$parent = $scope.$parent.$ctrl;
    }
    $onInit() {
        this.isDraggable = this.$attrs.hasOwnProperty('draggable');
        this.isPreviewable = this.$attrs.hasOwnProperty('previewable');
        this.isClickable = this.$attrs.hasOwnProperty('clickable');

        this.datasource = this.scene.datasource;
        if (this.repository) {
            this.updateThumbnails();
        }
    }

    $postLink() {
        this.$scope.$evalAsync(() => {
            const el = this.$element.find('img.item-img').get(0);
            $(el).on('error', () => {
                this.imageError = true;
                this.$scope.$evalAsync();
            });
        });
        this.$scope.$watch('$ctrl.scene.sceneOrder', (val) => {
            if (this.orderingInProgress) {
                this.manualOrderValue = val + 1;
            }
        });
    }

    $onChanges(changes) {
        if (changes.selected && changes.selected.hasOwnProperty('currentValue')) {
            this.selectedStatus = changes.selected.currentValue;
        }
        if (_.get(changes, 'scene.currentValue')) {
            this.manualOrderValue = _.get(changes, 'scene.currentValue.sceneOrder') + 1;
        }
        if (changes.repository && changes.repository.currentValue) {
            this.repository = changes.repository.currentValue;
            if (this.datasource) {
                this.updateThumbnails();
            }
        }
    }

    updateThumbnails() {
        if (this.scene.sceneType === 'COG') {
            let redBand = _.get(this.datasource.bands.find(x => {
                return x.name.toLowerCase() === 'red';
            }), 'number');
            let greenBand = _.get(this.datasource.bands.find(x => {
                return x.name.toLowerCase() === 'green';
            }), 'number');
            let blueBand = _.get(this.datasource.bands.find(x => {
                return x.name.toLowerCase() === 'blue';
            }), 'number');
            let bands = {};
            let atLeastThreeBands = this.datasource.bands.length >= 3;
            if (atLeastThreeBands) {
                bands.red = parseInt(redBand || 0, 10);
                bands.green = parseInt(greenBand || 1, 10);
                bands.blue = parseInt(blueBand || 2, 10);
            } else {
                bands.red = 0;
                bands.green = 0;
                bands.blue = 0;
            }
            this.sceneService.cogThumbnail(
                this.scene.id,
                this.authService.token(),
                128,
                128,
                bands.red,
                bands.green,
                bands.blue
            ).then(res => {
                // Because 504s aren't rejections, apparently
                if (_.isString(res)) {
                    this.thumbnail = `data:image/png;base64,${res}`;
                }
            }).catch(() => {
                this.imageError = true;
            });
        } else {
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
        }).result.catch(() => {});
    }

    onManualOrderToggle(event) {
        this.manualOrderValue = this.scene.sceneOrder + 1;
        if (event) {
            event.stopPropagation();
        }
        this.orderingInProgress = !this.orderingInProgress;

        if (this.orderingInProgress && !this.clickListener) {
            if (this.openDropdownListener) {
                this.openDropdownListener();
            }
            const onClick = () => {
                this.orderingInProgress = false;
                this.$document.off('click', this.clickListener);
                this.$scope.$evalAsync();
                delete this.clickListener;
                this.openDropdownListener = null;
            };
            this.clickListener = onClick;
            this.openDropdownListener = onClick;
            this.$document.on('click', onClick);
        } else if (!this.orderingInProgress && this.clickListener) {
            this.$document.off('click', this.clickListener);
            this.openDropdownListener = null;
            delete this.clickListener;
        }
    }

    onManualOrderConfirm() {
        this.onMove({scene: this.scene, position: this.manualOrderValue - 1});
        this.onManualOrderToggle();
    }

    onManualOrderCancel() {
        this.onManualOrderToggle();
    }

    positionIsValid(p) {
        return Number.isFinite(p) && p >= 0 && p !== this.scene.sceneOrder + 1;
    }
}

const SceneItemModule = angular.module('components.scenes.sceneItem', []);

SceneItemModule.component('rfSceneItem', SceneItemComponent);
SceneItemModule.controller('SceneItemController', SceneItemController);

export default SceneItemModule;

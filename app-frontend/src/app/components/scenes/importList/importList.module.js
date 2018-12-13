/* global _ */

import angular from 'angular';
import importListTpl from './importList.html';

const ImportListComponent = {
    templateUrl: importListTpl,
    controller: 'ImportListController',
    bindings: {
        platform: '<',
        ownershipType: '<'
    }
};

const pageSize = '10';

class ImportListController {
    constructor( // eslint-disable-line max-params
        $rootScope, $log, sceneService, $state,
        authService, modalService, paginationService,
        RasterFoundryRepository
    ) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
        this.currentOwnershipFilter = 'owned';
        this.repository = {
            name: 'Raster Foundry',
            service: RasterFoundryRepository
        };
    }

    $onInit() {
        this.populateImportList(this.$state.params.page || 1);
        this.sceneActions = [
            {
                label: 'Modify permissions',
                onClick: this.shareModal.bind(this),
                iconClass: 'icon-key'
            },
            {
                label: 'Download',
                onClick: this.downloadModal.bind(this),
                iconClass: 'icon-download'
            },
            {
                label: 'Delete',
                buttonClass: 'btn-danger',
                iconClass: 'icon-trash',
                onClick: this.deleteModal.bind(this)
            }
        ];
    }

    $onChanges(changes) {
        const ownerChange = _.get(changes, 'ownershipType.currentValue');
        if (ownerChange !== this.currentOwnershipFilter) {
            this.currentOwnershipFilter = ownerChange;
            this.populateImportList(1);
        }
    }

    populateImportList(page) {
        if (this.loading) {
            return;
        }
        delete this.errorMsg;
        this.loading = true;
        // save off selected scenes so you don't lose them during the refresh
        this.importList = [];
        this.sceneService.query(
            {
                sort: 'createdAt,desc',
                pageSize: pageSize,
                page: page - 1,
                ownershipType: this.ownershipType,
                exactCount: true
            }
        ).then((sceneResult) => {
            this.lastSceneResult = sceneResult;
            this.pagination = this.paginationService.buildPagination(sceneResult);
            this.paginationService.updatePageParam(page, '', null, {
                ownership: this.currentOwnershipFilter
            });
            this.importList = this.lastSceneResult.results;
            this.loading = false;
        }, () => {
            this.errorMsg = 'Server error.';
            this.loading = false;
        });
    }

    importModal() {
        this.modalService.open({
            component: 'rfSceneImportModal',
            resolve: {
                origin: () => 'raster'
            }
        }).result.catch(() => {});
    }

    downloadModal(scene) {
        this.modalService.open({
            component: 'rfSceneDownloadModal',
            resolve: {
                scene: () => scene
            }
        }).result.catch(() => {});
    }

    shareModal(scene) {
        this.modalService.open({
            component: 'rfPermissionModal',
            resolve: {
                object: () => scene,
                permissionsBase: () => 'scenes',
                objectType: () => 'SCENE',
                objectName: () => scene.name,
                platform: () => this.platform
            }
        }).result.catch(() => {});
    }

    deleteModal(scene) {
        const modal = this.modalService.open({
            component: 'rfFeedbackModal',
            resolve: {
                title: () => 'Delete scene',
                subtitle: () =>
                    'Deleting scenes cannot be undone.',
                content: () =>
                    '<h2>Do you wish to continue?</h2>'
                    + '<p>The scene will be pemanently '
                    + 'deleted. Projects and Analysis will '
                    + 'continue to function without the '
                    + 'scene.</p>',
                /* feedbackIconType : default, success, danger, warning */
                feedbackIconType: () => 'danger',
                feedbackIcon: () => 'icon-warning',
                feedbackBtnType: () => 'btn-danger',
                feedbackBtnText: () => 'Delete scene',
                cancelText: () => 'Cancel'
            }
        });

        modal.result.then(() => {
            this.sceneService.deleteScene(scene).then(
                () => {
                    this.$state.reload();
                },
                (err) => {
                    this.$log.debug('error deleting scene', err);
                }
            );
        }).catch(() => {});
    }

    shouldShowImportList() {
        return !this.loading && this.lastSceneResult &&
            this.lastSceneResult.count > this.lastSceneResult.pageSize && !this.errorMsg;
    }

    shouldShowImportBox() {
        return !this.loading && this.lastSceneResult &&
            this.lastSceneResult.count === 0 && !this.errorMsg;
    }

    onActionClick(event, action, scene) {
        event.stopPropagation();
        if (action.onClick) {
            action.onClick(scene);
        }
    }

    getActionIcon(action) {
        let classes = {};
        if (action.iconClass) {
            classes[action.iconClass] = true;
        }
        return classes;
    }

    getActionButtonClass(action) {
        let classes = {};
        if (action.buttonClass) {
            classes[action.buttonClass] = true;
        }
        return classes;
    }
}

const ImportListModule = angular.module('components.scenes.importList', []);

ImportListModule.component('rfImportList', ImportListComponent);
ImportListModule.controller('ImportListController', ImportListController);

export default ImportListModule;

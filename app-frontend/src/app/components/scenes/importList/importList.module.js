import angular from 'angular';
import { OrderedMap } from 'immutable';
import importListTpl from './importList.html';
import _ from 'lodash';

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
        $rootScope,
        $log,
        $state,
        $q,
        sceneService,
        authService,
        modalService,
        paginationService,
        permissionsService,
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
        const downloadAction = {
            label: 'Download',
            onClick: this.downloadModal.bind(this),
            iconClass: 'icon-download',
            order: 0
        };
        const modifyAction = {
            label: 'Modify permissions',
            onClick: this.shareModal.bind(this),
            iconClass: 'icon-key',
            order: 1
        };
        const deleteAction = {
            label: 'Delete',
            buttonClass: 'btn-danger',
            iconClass: 'icon-trash',
            onClick: this.deleteModal.bind(this),
            order: 2
        };
        this.sceneActionCatalog = {
            VIEW: [],
            EDIT: [modifyAction],
            DEACTIVATE: [],
            ANNOTATE: [],
            EXPORT: [],
            DOWNLOAD: [downloadAction],
            DELETE: [deleteAction]
        };
        this.sceneActions = new OrderedMap();
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
        this.sceneService
            .query(
                Object.assign(
                    {
                        sort: 'createdAt,desc',
                        pageSize: pageSize,
                        page: page - 1,
                        exactCount: true
                    },
                    this.ownershipType ? { ownershipType: this.ownershipType } : null
                )
            )
            .then(
                sceneResult => {
                    this.lastSceneResult = sceneResult;
                    this.pagination = this.paginationService.buildPagination(sceneResult);
                    this.paginationService.updatePageParam(page, '', null, {
                        ownership: this.currentOwnershipFilter
                    });
                    this.importList = this.lastSceneResult.results;
                    this.updateSceneActions();
                    this.loading = false;
                },
                () => {
                    this.errorMsg = 'Server error.';
                    this.loading = false;
                }
            );
    }

    updateSceneActions() {
        // TODO make sure this doesn't kill stuff if you do multiple searches / pages in a row
        this.sceneActions = new OrderedMap();
        let permissionPromises = this.importList.map(s => {
            if (s.owner === this.authService.user.id) {
                return this.$q
                    .resolve(Object.keys(this.sceneActionCatalog).map(k => ({ actionType: k })))
                    .then(permissions => ({ id: s.id, permissions }));
            }
            return this.permissionsService
                .getEditableObjectPermissions('scenes', 'SCENE', s, this.authService.user)
                .then(permissions => ({ id: s.id, permissions }));
        });
        this.$q.all(permissionPromises).then(scenePermissionList => {
            this.sceneActions = new OrderedMap(
                scenePermissionList.map(({ id, permissions }) => {
                    return [
                        id,
                        _.filter(
                            _.flatten(permissions.map(p => this.sceneActionCatalog[p.actionType]))
                        )
                    ];
                })
            );
        });
    }

    importModal() {
        this.modalService
            .open({
                component: 'rfSceneImportModal',
                resolve: {
                    origin: () => 'raster'
                }
            })
            .result.catch(() => {});
    }

    downloadModal(scene) {
        this.modalService
            .open({
                component: 'rfSceneDownloadModal',
                resolve: {
                    scene: () => scene
                }
            })
            .result.catch(() => {});
    }

    shareModal(scene) {
        this.modalService
            .open({
                component: 'rfPermissionModal',
                resolve: {
                    object: () => scene,
                    permissionsBase: () => 'scenes',
                    objectType: () => 'SCENE',
                    objectName: () => scene.name,
                    platform: () => this.platform
                }
            })
            .result.then(() => {
                this.updateSceneActions();
            })
            .catch(() => {});
    }

    deleteModal(scene) {
        const modal = this.modalService.open({
            component: 'rfFeedbackModal',
            resolve: {
                title: () => 'Delete scene',
                subtitle: () => 'Deleting scenes cannot be undone.',
                content: () =>
                    '<h2>Do you wish to continue?</h2>' +
                    '<p>The scene will be pemanently ' +
                    'deleted. Projects and Analysis will ' +
                    'continue to function without the ' +
                    'scene.</p>',
                /* feedbackIconType : default, success, danger, warning */
                feedbackIconType: () => 'danger',
                feedbackIcon: () => 'icon-warning',
                feedbackBtnType: () => 'btn-danger',
                feedbackBtnText: () => 'Delete scene',
                cancelText: () => 'Cancel'
            }
        });

        modal.result
            .then(() => {
                this.sceneService.deleteScene(scene).then(
                    () => {
                        this.$state.reload();
                    },
                    err => {
                        this.$log.debug('error deleting scene', err);
                    }
                );
            })
            .catch(() => {});
    }

    shouldShowImportList() {
        return (
            !this.loading &&
            this.lastSceneResult &&
            this.lastSceneResult.count > this.lastSceneResult.pageSize &&
            !this.errorMsg
        );
    }

    shouldShowImportBox() {
        return (
            !this.loading &&
            this.lastSceneResult &&
            this.lastSceneResult.count === 0 &&
            !this.errorMsg
        );
    }
}

const ImportListModule = angular.module('components.scenes.importList', []);

ImportListModule.component('rfImportList', ImportListComponent);
ImportListModule.controller('ImportListController', ImportListController);

export default ImportListModule;

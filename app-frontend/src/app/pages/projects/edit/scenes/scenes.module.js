/* globals document */
import angular from 'angular';

class ProjectsScenesController {
    constructor( // eslint-disable-line max-params
        $log, $state, $scope, $timeout,
        modalService, projectService, RasterFoundryRepository, uploadService,
        sceneService, authService, paginationService,
        platform
    ) {
        'ngInject';
        $scope.autoInject(this, arguments);
    }

    $onInit() {
        this.$parent = this.$scope.$parent.$ctrl;
        this.projectId = this.$parent.projectId;
        this.repository = {
            name: 'Raster Foundry',
            service: this.RasterFoundryRepository
        };
        this.pendingImports = 0;
        this.checkPendingImports();
        if (!this.$parent.currentRequest) {
            this.$parent.fetchPage();
        }
        this.getPendingSceneCount();
        // eslint-disable-next-line
        let thisItem = this;
        this.treeOptions = {
            dragStart: function (e) {
                thisItem.onSceneDragStart(e);
            },
            dropped: function (e) {
                thisItem.onSceneDropped(e.source.nodesScope.$modelValue);
            }
        };
    }

    getPendingSceneCount() {
        if (!this.pendingSceneRequest) {
            this.pendingSceneRequest = this.projectService.getProjectScenes(this.projectId, {
                pending: true,
                pageSize: 0
            });
            this.pendingSceneRequest.then((paginatedResponse) => {
                this.pendingSceneCount =
                    this.paginationService.buildPagination(paginatedResponse).count;
            });
        }
        return this.pendingSceneRequest;
    }

    onSceneDragStart(e) {
        this.setDragPlaceholderHeight(e);
    }

    setDragPlaceholderHeight(e) {
        const ele = angular.element(document.querySelector('.list-group-item'));
        const placeholder = angular.element(e.elements.placeholder);
        placeholder.css('height', ele.css('height'));
    }

    onSceneDropped(orderedScenes) {
        // get order using paginator
        const pagination = this.$parent.pagination;
        this.$parent.sceneList = this.$parent.sceneList.map(
            (scene, index) => Object.assign(scene, {sceneOrder: index})
        );
        let orderedSceneIds = orderedScenes.map(s => s.id);
        this.updateSceneOrder(orderedSceneIds);
    }

    onMove(scene, position) {
        function arrayMove(arr, oldIndex, newIndex) {
            if (newIndex >= arr.length) {
                let k = newIndex - arr.length + 1;
                // eslint-disable-next-line
                while (k--) {
                    // eslint-disable-next-line
                    arr.push(undefined);
                }
            }
            arr.splice(newIndex, 0, arr.splice(oldIndex, 1)[0]);
        }
        let p = position;
        if (p < 0) {
            p = 0;
        } else if (p > this.$parent.pagination.count - 1) {
            p = this.$parent.pagination.count - 1;
        }

        arrayMove(
            this.$parent.sceneList,
            this.$parent.sceneList.findIndex((s) => s.id === scene.id),
            p
        );
        this.onSceneDropped(this.$parent.sceneList);
    }

    removeSceneFromProject(scene, $event) {
        $event.stopPropagation();
        $event.preventDefault();
        this.projectService.removeScenesFromProject(this.$parent.projectId, [scene.id]).then(
            () => {
                this.$parent.removeHoveredScene();
                this.$parent.fetchPage();
                this.$parent.layerFromProject();
            },
            () => {
                this.$log.log('error removing scene from project');
            }
        );
    }

    shareModal(project) {
        this.modalService.open({
            component: 'rfPermissionModal',
            size: 'med',
            resolve: {
                object: () => project,
                permissionsBase: () => 'projects',
                objectType: () => 'PROJECT',
                objectName: () => project.name,
                platform: () => this.platform
            }
        });
    }

    openImportModal() {
        const activeModal = this.modalService.open({
            component: 'rfSceneImportModal',
            resolve: {
                project: () => this.$parent.project,
                origin: () => 'project'
            }
        });

        activeModal.result.then(results => {
            this.checkPendingImports();
        });
    }

    updateSceneOrder(orderedSceneIds) {
        this.projectService.updateSceneOrder(this.$parent.projectId, orderedSceneIds).then(() => {
            this.$parent.layerFromProject();
        });
    }

    gotoBrowse() {
        this.$parent.getMap().then(mapWrapper => {
            const bbox = mapWrapper.map.getBounds();
            this.$state.go('projects.edit.browse', {sceneid: null, bbox: bbox.toBBoxString()});
        });
    }

    sceneOrderTracker(scene) {
        Object.assign(scene, {'$$hashKey': scene.id});
        return scene.$$hashKey;
    }

    checkPendingImports() {
        this.uploadService.query({
            uploadStatus: 'UPLOADED',
            projectId: this.projectId,
            pageSize: 0
        }).then(uploads => {
            this.pendingImports = uploads.count;
        });
    }

    setHoveredScene(scene) {
        if (scene !== this.hoveredScene) {
            this.hoveredScene = scene;
            this.$parent.getMap().then((map) => {
                if (scene.sceneType !== 'COG' && scene.statusFields.ingestStatus === 'INGESTED') {
                    this.$parent.setHoveredScene(scene);
                } else {
                    map.setThumbnail(scene, this.repository);
                }
            });
        }
    }

    removeHoveredScene() {
        this.$parent.getMap().then((map) => {
            if (this.hoveredScene.sceneType !== 'COG' &&
                this.hoveredScene.statusFields.ingestStatus === 'INGESTED') {
                this.$parent.removeHoveredScene();
            } else {
                map.deleteThumbnail();
            }
            delete this.hoveredScene;
        });
    }

    downloadSceneModal(scene) {
        this.modalService.open({
            component: 'rfSceneDownloadModal',
            resolve: {
                scene: () => scene
            }
        });
    }
}

const ProjectsScenesModule = angular.module('pages.projects.edit.scenes', ['ui.tree']);

ProjectsScenesModule.controller(
    'ProjectsScenesController', ProjectsScenesController
);

export default ProjectsScenesModule;

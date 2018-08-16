/* globals document */
import angular from 'angular';

class ProjectsScenesController {
    constructor( // eslint-disable-line max-params
        $log, $state, $scope, $timeout,
        modalService, projectService, RasterFoundryRepository,
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

    onSceneDragStart(e) {
        this.setDragPlaceholderHeight(e);
    }

    setDragPlaceholderHeight(e) {
        const ele = angular.element(document.querySelector('.list-group-item'));
        const placeholder = angular.element(e.elements.placeholder);
        placeholder.css('height', ele.css('height'));
    }

    onSceneDropped(orderedScenes) {
        let orderedSceneIds = orderedScenes.map(s => s.id);
        this.updateSceneOrder(orderedSceneIds);
    }

    removeSceneFromProject(scene, $event) {
        $event.stopPropagation();
        $event.preventDefault();
        this.projectService.removeScenesFromProject(this.$parent.projectId, [scene.id]).then(
            () => {
                this.$parent.removeHoveredScene();
                this.$parent.getSceneList();
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
        this.modalService.open({
            component: 'rfSceneImportModal',
            resolve: {
                project: () => this.$parent.project,
                origin: () => 'project'
            }
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
}

const ProjectsScenesModule = angular.module('pages.projects.edit.scenes', ['ui.tree']);

ProjectsScenesModule.controller(
    'ProjectsScenesController', ProjectsScenesController
);

export default ProjectsScenesModule;

/* globals document */
import angular from 'angular';

class ProjectsScenesController {
    constructor( // eslint-disable-line max-params
        $log, $state, $scope, modalService, projectService, RasterFoundryRepository
    ) {
        'ngInject';
        this.$log = $log;
        this.modalService = modalService;
        this.projectId = $state.params.projectid;
        this.$parent = $scope.$parent.$ctrl;
        this.projectService = projectService;
        this.repository = {
            name: 'Raster Foundry',
            service: RasterFoundryRepository
        };
    }

    $onInit() {
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
        this.projectService.removeScenesFromProject(this.projectId, [scene.id]).then(
            () => {
                this.$parent.removeHoveredScene();
                this.$parent.getSceneList();
            },
            () => {
                this.$log.log('error removing scene from project');
            }
        );
    }

    openImportModal() {
        this.modalService.open({
            component: 'rfSceneImportModal',
            resolve: {
                project: () => this.project
            }
        });
    }

    updateSceneOrder(orderedSceneIds) {
        this.projectService.updateSceneOrder(this.projectId, orderedSceneIds).then(() => {
            this.$parent.layerFromProject();
        });
    }
}

const ProjectsScenesModule = angular.module('pages.projects.edit.scenes', ['ui.tree']);

ProjectsScenesModule.controller(
    'ProjectsScenesController', ProjectsScenesController
);

export default ProjectsScenesModule;

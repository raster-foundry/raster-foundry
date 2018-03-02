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
            dropped: function (e) {
                thisItem.onSceneDropped(e.source.nodesScope.$modelValue);
            }
        };
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

    openSceneDetailModal(scene) {
        this.$parent.removeHoveredScene();

        this.modalService.open({
            component: 'rfSceneDetailModal',
            resolve: {
                scene: () => scene,
                repository: () => this.repository
            }
        });
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

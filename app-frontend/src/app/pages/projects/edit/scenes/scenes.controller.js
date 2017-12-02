export default class ProjectsScenesController {
    constructor( // eslint-disable-line max-params
        $log, $state, $scope, modalService, projectService
    ) {
        'ngInject';
        this.$log = $log;
        this.$state = $state;
        this.modalService = modalService;
        this.projectId = $state.params.projectid;
        this.$parent = $scope.$parent.$ctrl;
        this.projectService = projectService;
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
                scene: () => scene
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
}

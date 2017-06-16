export default class ProjectsScenesController {
    constructor( // eslint-disable-line max-params
        $log, $state, $scope, $uibModal, projectService
    ) {
        'ngInject';
        this.$log = $log;
        this.$state = $state;
        this.$uibModal = $uibModal;
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

        if (this.activeModal) {
            this.activeModal.dismiss();
        }

        this.activeModal = this.$uibModal.open({
            component: 'rfSceneDetailModal',
            resolve: {
                scene: () => scene
            }
        });
    }
}

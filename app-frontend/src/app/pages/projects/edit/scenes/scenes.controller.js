export default class ProjectsScenesController {
    constructor( // eslint-disable-line max-params
        $log, $state, $scope, projectService
    ) {
        'ngInject';
        this.$log = $log;
        this.$state = $state;
        this.projectId = $state.params.projectid;
        this.$parent = $scope.$parent.$ctrl;
        this.projectService = projectService;
    }

    removeSceneFromProject(scene) {
        this.projectService.removeScenesFromProject(this.projectId, [scene.id]).then(
            () => {
                this.$parent.getSceneList();
            },
            () => {
                this.$log.log('error removing scene from project');
            });
    }
}

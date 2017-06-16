export default class ProjectDetailScenesController {
    constructor($state, $scope, $timeout, projectService) {
        'ngInject';
        this.$state = $state;
        this.$scope = $scope;
        this.$timeout = $timeout;
        this.projectService = projectService;
    }

    $onInit() {
        this.isLoadingProject = true;
        this.$scope.$parent.$ctrl.fetchProject().then(p => {
            this.project = p;
            this.isLoadingProject = false;
            this.populateSceneList(this.$state.params.page || 1);
        });
    }

    populateSceneList(page) {
        if (this.isLoadingScenes) {
            return;
        }
        delete this.errorMsg;
        this.isLoadingScenes = true;
        // save off selected scenes so you don't lose them during the refresh
        this.sceneList = [];
        this.projectService.getProjectScenes(
            {
                sort: 'createdAt,desc',
                pageSize: '10',
                page: page - 1,
                projectId: this.project.id
            }
        ).then((sceneResult) => {
            this.lastSceneResult = sceneResult;
            this.numPaginationButtons = 6 - sceneResult.page % 10;
            if (this.numPaginationButtons < 3) {
                this.numPaginationButtons = 3;
            }
            this.currentPage = sceneResult.page + 1;
            let replace = !this.$state.params.page;
            this.$state.transitionTo(
                this.$state.$current.name,
                {
                    projectid: this.project.id, page: this.currentPage
                },
                {
                    location: replace ? 'replace' : true,
                    notify: false
                }
            );
            this.sceneList = this.lastSceneResult.results;
            this.isLoadingScenes = false;
        }, () => {
            this.errorMsg = 'Server error.';
            this.isLoadingScenes = false;
        });
    }
}

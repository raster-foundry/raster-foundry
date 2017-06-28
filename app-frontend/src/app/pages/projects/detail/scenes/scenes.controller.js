export default class ProjectDetailScenesController {
    constructor($state, $scope, $timeout, $uibModal, projectService) {
        'ngInject';
        this.$state = $state;
        this.$scope = $scope;
        this.$timeout = $timeout;
        this.$uibModal = $uibModal;
        this.projectService = projectService;
    }

    $onInit() {
        this.isLoadingProject = true;
        this.$scope.$parent.$ctrl.fetchProject().then(p => {
            this.project = p;
            this.isLoadingProject = false;
            this.populateSceneList(this.$state.params.page);
        });
    }

    populateSceneList(page) {
        const requestPage = Math.max(page | 0, 1) - 1;
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
                page: requestPage,
                projectId: this.project.id
            }
        ).then((sceneResult) => {
            this.lastSceneResult = sceneResult;
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

    openSceneDetailModal(scene) {
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

    removeScene(scene, event) {
        if (event) {
            event.stopPropagation();
            event.preventDefault();
        }
        this.projectService.removeScenesFromProject(this.project.id, [ scene.id ]).then(
            () => {
                this.populateSceneList(this.currentPage);
            },
            (err) => {
                // later on, use toasts or something instead of a debug message
                this.$log.debug('Error removing scenes from project.', err);
                this.populateSceneList(this.currentPage);
            }
        );
    }
}

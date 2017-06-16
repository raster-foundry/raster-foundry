class ProjectsListController {
    constructor( // eslint-disable-line max-params
        $log, $state, $uibModal, $scope, projectService, userService
    ) {
        'ngInject';
        this.$log = $log;
        this.$state = $state;
        this.$uibModal = $uibModal;
        this.projectService = projectService;
        this.userService = userService;
        this.$scope = $scope;

        this.projectList = [];
        this.populateProjectList($state.params.page || 1);
    }

    populateProjectList(page) {
        if (this.loading) {
            return;
        }
        delete this.errorMsg;
        this.loading = true;
        this.projectService.query(
            {
                sort: 'createdAt,desc',
                pageSize: 10,
                page: page - 1
            }
        ).then(
            (projectResult) => {
                this.lastProjectResult = projectResult;
                this.numPaginationButtons = 6 - projectResult.page % 10;
                if (this.numPaginationButtons < 3) {
                    this.numPaginationButtons = 3;
                }
                this.currentPage = projectResult.page + 1;
                let replace = !this.$state.params.page;
                this.$state.transitionTo(
                    this.$state.$current.name,
                    {page: this.currentPage},
                    {
                        location: replace ? 'replace' : true,
                        notify: false
                    }
                );
                this.projectList = this.lastProjectResult.results;
                this.loading = false;
                this.projectList.forEach((project) => {
                    this.getProjectScenesCount(project);
                });
            },
            () => {
                this.errorMsg = 'Server error.';
                this.loading = false;
            }
        );
    }

    getProjectScenesCount(project) {
        this.projectService.getProjectSceneCount({projectId: project.id}).then(
            (sceneResult) => {
                let bupdate = this.projectList.find((b) => b.id === project.id);
                bupdate.scenes = sceneResult.count;
            }
        );
    }

    viewProjectDetail(project) {
        this.$state.go('^.detail', {project: project, projectid: project.id});
    }

    createNewProject() {
        if (this.newProjectModal) {
            this.newProjectModal.dismiss();
        }

        this.newProjectModal = this.$uibModal.open({
            component: 'rfProjectCreateModal'
        });

        this.newProjectModal.result.then((data) => {
            if (data && data.reloadProjectList) {
                this.populateProjectList(1);
            }
        });

        return this.newProjectModal;
    }
}

export default ProjectsListController;

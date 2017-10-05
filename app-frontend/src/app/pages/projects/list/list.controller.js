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

    populateProjectList(page = 1) {
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
                this.updatePagination(projectResult);
                this.currentPage = page;
                let replace = !this.$state.params.page;
                this.$state.transitionTo(
                    this.$state.$current.name,
                    {page: this.currentPage},
                    {
                        location: replace ? 'replace' : true,
                        notify: false
                    }
                );
                this.lastProjectResult = projectResult;
                this.projectList = projectResult.results;
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

    updatePagination(data) {
        this.pagination = {
            show: data.count > data.pageSize,
            count: data.count,
            currentPage: data.page + 1,
            startingItem: data.page * data.pageSize + 1,
            endingItem: Math.min((data.page + 1) * data.pageSize, data.count),
            hasNext: data.hasNext,
            hasPrevious: data.hasPrevious
        };
    }

    search(value) {
        this.searchString = value;
        if (this.searchString) {
            this.projectService.searchQuery().then(projects => {
                this.projectList = projects;
            });
        } else {
            this.populateProjectList();
        }
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

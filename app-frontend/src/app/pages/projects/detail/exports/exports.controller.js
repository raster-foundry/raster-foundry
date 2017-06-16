export default class ProjectDetailExportsController {
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
            this.populateExportList(this.$state.params.page || 1);
        });
    }

    populateExportList(page) {
        if (this.isLoadingExports) {
            return;
        }
        delete this.errorMsg;
        this.isLoadingExports = true;
        // save off selected scenes so you don't lose them during the refresh
        this.exportList = [];
        this.projectService.listExports(
            {
                sort: 'createdAt,desc',
                pageSize: '10',
                page: page - 1,
                project: this.project.id
            }
        ).then(exportResult => {
            this.lastExportResult = exportResult;
            this.numPaginationButtons = Math.max(6 - exportResult.page % 10, 3);
            this.currentPage = exportResult.page + 1;
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
            this.exportList = this.lastExportResult.results;
            this.isLoadingExports = false;
        }, () => {
            this.errorMsg = 'Server error.';
            this.isLoadingExports = false;
        });
    }
}

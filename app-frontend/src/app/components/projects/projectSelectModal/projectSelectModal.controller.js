export default class ProjectSelectModalController {
    constructor($log, $state, projectService) {
        'ngInject';
        this.$state = $state;
        this.projectService = projectService;
        this.populateProjectList(1);
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
                pageSize: 5,
                page: page - 1
            }
        ).then((projectResult) => {
            this.lastProjectResult = projectResult;
            this.numPaginationButtons = 6 - projectResult.page % 5;
            if (this.numPaginationButtons < 3) {
                this.numPaginationButtons = 3;
            }
            this.currentPage = projectResult.page + 1;
            this.projectList = this.lastProjectResult.results;
            this.loading = false;
        }, () => {
            this.errorMsg = 'Server error.';
            this.loading = false;
        });
    }

    setSelected(project) {
        this.close({$value: project});
    }
}

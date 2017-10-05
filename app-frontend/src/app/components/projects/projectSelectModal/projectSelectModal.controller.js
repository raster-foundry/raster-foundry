export default class ProjectSelectModalController {
    constructor($log, $state, projectService) {
        'ngInject';
        this.$state = $state;
        this.projectService = projectService;
        this.populateProjectList(1);
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
                pageSize: 5,
                page: page - 1
            }
        ).then((projectResult) => {
            this.updatePagination(projectResult);
            this.lastProjectResult = projectResult;
            this.currentPage = page;
            this.projectList = this.lastProjectResult.results;
            this.loading = false;
        }, () => {
            this.errorMsg = 'Server error.';
            this.loading = false;
        });
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



    setSelected(project) {
        this.close({$value: project});
    }
}

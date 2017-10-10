class LabBrowseToolsController {
    constructor($state, toolService) {
        'ngInject';
        this.$state = $state;
        this.toolService = toolService;
    }

    $onInit() {
        this.fetchToolList(this.$state.params.page);
    }

    fetchToolList(page = 1) {
        this.loadingTools = true;
        this.toolService.toolRunQuery(
            {
                pageSize: 10,
                page: page - 1
            }
        ).then(d => {
            this.currentPage = page;
            this.updatePagination(d);
            let replace = !this.$state.params.page;
            this.$state.transitionTo(
                this.$state.$current.name,
                {page: this.currentPage},
                {
                    location: replace ? 'replace' : true,
                    notify: false
                }
            );
            this.lastToolResponse = d;
            this.toolList = d.results;
            this.loadingTools = false;
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

    formatToolVisibility(visibility) {
        const v = visibility.toUpperCase();
        if (v === 'PUBLIC') {
            return 'Public';
        }
        return 'Private';
    }
}

export default angular.module('pages.lab.browse.tools', [])
    .controller('LabBrowseToolsController', LabBrowseToolsController);

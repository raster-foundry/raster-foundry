export default class SelectToolModalController {
    constructor($log, $state, toolService) {
        'ngInject';
        this.$state = $state;
        this.toolService = toolService;
        this.fetchToolList();
    }

    fetchToolList() {
        this.loadingTools = true;
        this.toolService.query().then(d => {
            this.updatePagination(d);
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

    selectTool(toolData) {
        this.close({$value: toolData});
    }
}

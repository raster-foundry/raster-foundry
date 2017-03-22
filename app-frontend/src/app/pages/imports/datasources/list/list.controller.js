class DatasourceListController {
    constructor($state, datasourceService) {
        'ngInject';
        this.$state = $state;
        this.datasourceService = datasourceService;
    }

    $onInit() {
        this.datasources = {};
        this.currentPage = 1;
        this.pageSize = 10;
        this.loadDatasources();
    }

    $onDestroy() {

    }

    shouldShowPlaceholder() {
        return !this.isLoadingDatasources &&
            this.datasources.count &&
            this.datasources.count === 0;
    }

    shouldShowList() {
        return !this.isLoadingDatasources &&
            this.datasources.count &&
            this.datasources.count > 0;
    }

    shouldShowPagination() {
        return !this.isLoadingDatasources &&
            !this.isErrorLoadingDatasources &&
            this.datasources.count &&
            this.datasources.count > this.pageSize;
    }

    updateQueryParameters() {
        const replace = !this.$state.params.page;
        this.$state.transitionTo(
            this.$state.$current.name,
            {
                page: this.currentPage
            },
            {
                location: replace ? 'replace' : true,
                notify: false
            }
        );
    }

    loadDatasources(page = 1) {
        this.isLoadingDatasources = true;
        this.isErrorLoadingDatasources = false;
        this.datasourceService.query({
            sort: 'createdAt,desc',
            pageSize: this.pageSize,
            page: page - 1
        }).then(
            datasourceResponse => {
                this.datasources = datasourceResponse;
                this.currentPage = datasourceResponse.page + 1;
                this.updateQueryParameters();
            },
            () => {
                this.isErrorLoadingDatasources = true;
            })
            .finally(() => {
                this.isLoadingDatasources = false;
            }
        );
    }
}

export default DatasourceListController;

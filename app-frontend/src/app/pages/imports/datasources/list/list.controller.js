/* global BUILDCONFIG */

class DatasourceListController {
    constructor($state, datasourceService, modalService, $filter) {
        'ngInject';
        this.$state = $state;
        this.$filter = $filter;
        this.datasourceService = datasourceService;
        this.modalService = modalService;
    }

    $onInit() {
        this.BUILDCONFIG = BUILDCONFIG;
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
            this.datasources.count > 0 &&
            !this.searchString;
    }

    shouldShowPagination() {
        return !this.isLoadingDatasources &&
            !this.isErrorLoadingDatasources &&
            this.datasources.count &&
            this.datasources.count > this.pageSize &&
            !this.searchString;
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

    createDatasourceModal() {
        this.modalService.open({
            component: 'rfDatasourceCreateModal'
        }).result.then(() => {
            this.loadDatasources();
            this.searchString = '';
        });
    }

    loadAllDatasources() {
        if (this.isLoadingDatasources) {
            return;
        }
        this.isLoadingDatasources = true;
        this.isErrorLoadingDatasources = false;
        this.datasourceService.query({
            sort: 'createdAt,desc',
            pageSize: this.pageSize,
            page: 0
        }).then((result) => {
            this.cachedDatasources = result.results;
            let currentCache = this.cachedDatasources;
            this.updateFilter();
            if (result.count > this.pageSize) {
                let pages = Math.ceil(result.count / this.pageSize) - 1;
                for (let i = 1; i <= pages; i = i + 1) {
                    this.datasourceService.query({
                        sort: 'createdAt,desc',
                        pageSize: this.pageSize,
                        page: i
                    }).then((pageresult) => {
                        if (currentCache === this.cachedDatasources) {
                            this.cachedDatasources = this.cachedDatasources.concat(
                                pageresult.results
                            );
                            this.updateFilter(pageresult.results);
                            if (this.cachedDatasources.length === result.count) {
                                this.isLoadingDatasources = false;
                                this.isErrorLoadingDatasources = false;
                            }
                        }
                    }, () => {
                        if (currentCache === this.cachedDatasources) {
                            this.isErrorLoadingDatasources = true;
                        }
                    });
                }
            } else if (this.cachedDatasources.length === result.count) {
                this.isLoadingDatasources = false;
                this.isErrorLoadingDatasources = false;
            }
        });
    }

    updateFilter(datasources) {
        if (datasources) {
            let filteredNewSources = this.$filter('filter')(datasources, {name: this.searchString});
            this.filteredSources = this.filteredSources ?
                this.filteredSources.concat(filteredNewSources) : filteredNewSources;
        } else {
            this.filteredSources = this.$filter('filter')(
                this.cachedDatasources, {name: this.searchString}
            );
        }
    }

    search(searchString) {
        this.searchString = searchString;
        if (!this.cachedDatasources || this.isErrorLoadingDatasources) {
            this.loadAllDatasources();
        }
        this.updateFilter();
    }
}

export default DatasourceListController;

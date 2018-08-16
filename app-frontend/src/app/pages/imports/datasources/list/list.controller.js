/* global BUILDCONFIG */

class DatasourceListController {
    constructor(
        $scope, $state, $stateParams, $filter,
        datasourceService, modalService, paginationService
    ) {
        'ngInject';
        $scope.autoInject(this, arguments);
        this.pageSize = 10;
    }

    $onInit() {
        this.BUILDCONFIG = BUILDCONFIG;
        this.fetchPage();
    }

    shouldShowPlaceholder() {
        return !this.currentQuery &&
            !this.fetchError &&
            (!this.search || !this.search.length) &&
            this.datasources &&
            this.datasources.length === 0;
    }

    shouldShowEmptySearch() {
        return !this.currentQuery &&
            !this.fetchError &&
            this.search && this.search.length &&
            this.datasources && !this.datasources.length;
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

    fetchPage(page = this.$stateParams.page || 1, search = this.$stateParams.search) {
        this.search = search;
        delete this.fetchError;
        let currentQuery = this.datasourceService.query({
            sort: 'createdAt,desc',
            pageSize: this.pageSize,
            page: page - 1,
            search
        }).then(paginatedResponse => {
            this.datasources = paginatedResponse.results;
            this.pagination = this.paginationService.buildPagination(paginatedResponse);
            this.paginationService.updatePageParam(page, search);
            if (this.currentQuery === currentQuery) {
                delete this.fetchError;
            }
        }, (e) => {
            if (this.currentQuery === currentQuery) {
                this.fetchError = e;
            }
        }).finally(() => {
            if (this.currentQuery === currentQuery) {
                delete this.currentQuery;
            }
        });
        this.currentQuery = currentQuery;
    }

    createDatasourceModal() {
        this.modalService.open({
            component: 'rfDatasourceCreateModal'
        }).result.then(() => {
            this.loadDatasources();
            this.searchString = '';
        }, () => {
            this.loadDatasources();
            this.searchString = '';
        });
    }
}

export default DatasourceListController;

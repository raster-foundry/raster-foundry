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

    fetchPage(page = this.$state.params.page || 1, search = this.$state.params.search) {
        this.search = search && search.length ? search : null;
        delete this.fetchError;
        this.datasources = [];
        let currentQuery = this.datasourceService.query({
            sort: 'createdAt,desc',
            pageSize: this.pageSize,
            page: page - 1,
            search: this.search
        }).then(paginatedResponse => {
            this.datasources = paginatedResponse.results;
            this.pagination = this.paginationService.buildPagination(paginatedResponse);
            this.paginationService.updatePageParam(page, this.search);
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
            this.search = '';
            this.fetchPage(1, '');
        });
    }
}

export default DatasourceListController;

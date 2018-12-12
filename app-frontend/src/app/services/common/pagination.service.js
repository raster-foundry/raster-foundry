/* global _ */

export default (app) => {
    class PaginationService {
        constructor($state) {
            this.$state = $state;
        }

        buildPagination(paginatedResponse) {
            return {
                pageSize: paginatedResponse.pageSize,
                show: paginatedResponse.count > paginatedResponse.pageSize,
                count: paginatedResponse.count,
                currentPage: paginatedResponse.page + 1,
                startingItem: Math.min(
                    paginatedResponse.page * paginatedResponse.pageSize + 1,
                    paginatedResponse.count
                ),
                endingItem: Math.min(
                    (paginatedResponse.page + 1) * paginatedResponse.pageSize,
                    paginatedResponse.count
                ),
                hasNext: paginatedResponse.hasNext,
                hasPrevious: paginatedResponse.hasPrevious
            };
        }

        updatePageParam(page, search, sort, extraParams = {}) {
            let replace = !this.$state.params.page ||
                !this.$state.params.search || !this.$state.params.search;

            this.$state.go(
                this.$state.$current.name,
                { ...extraParams, page, search, sort },
                {
                    location: replace ? 'replace' : true,
                    notify: false
                }
            );
        }


    }

    app.service('paginationService', PaginationService);
};

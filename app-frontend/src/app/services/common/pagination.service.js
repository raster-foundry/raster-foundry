/* global _ */

export default (app) => {
    class PaginationService {
        constructor($state) {
            this.$state = $state;
        }

        buildPagination(paginatedResponse) {
            let startingItem = paginatedResponse.page * paginatedResponse.pageSize + 1;
            let endingItem = Math.min(
                (paginatedResponse.page + 1) * paginatedResponse.pageSize,
                paginatedResponse.count
            );
            if (endingItem === 0) {
                startingItem = 0;
            }
            return {
                pageSize: paginatedResponse.pageSize,
                show: paginatedResponse.count > paginatedResponse.pageSize,
                count: paginatedResponse.count,
                currentPage: paginatedResponse.page + 1,
                startingItem: startingItem,
                endingItem: endingItem,
                hasNext: paginatedResponse.hasNext,
                hasPrevious: paginatedResponse.hasPrevious
            };
        }

        updatePageParam(page, search, sort) {
            let replace = !this.$state.params.page ||
                !this.$state.params.search || !this.$state.params.search;
            this.$state.go(
                this.$state.$current.name,
                { page, search, sort },
                {
                    location: replace ? 'replace' : true,
                    notify: false
                }
            );
        }


    }

    app.service('paginationService', PaginationService);
};

/* global _ */

export default (app) => {
    class PaginationService {
        constructor($state) {
            this.$state = $state;
        }

        buildPagedSearch(context) {
            const searchFn = function (search) {
                this.searchTerm = search;
                this.fetchPage();
            };
            return _.debounce(searchFn.bind(context), 500, {
                leading: false,
                trailing: true
            });
        }

        buildPagination(paginatedResponse) {
            return {
                pageSize: paginatedResponse.pageSize,
                show: paginatedResponse.count > paginatedResponse.pageSize,
                count: paginatedResponse.count,
                currentPage: paginatedResponse.page + 1,
                startingItem: paginatedResponse.page * paginatedResponse.pageSize + 1,
                endingItem: Math.min(
                    (paginatedResponse.page + 1) * paginatedResponse.pageSize,
                    paginatedResponse.count
                ),
                hasNext: paginatedResponse.hasNext,
                hasPrevious: paginatedResponse.hasPrevious
            };
        }

        updatePageParam(page) {
            let replace = !this.$state.params.page;
            this.$state.go(
                this.$state.$current.name,
                { page },
                {
                    location: replace ? 'replace' : true,
                    notify: false
                }
            );
        }


    }

    app.service('paginationService', PaginationService);
};

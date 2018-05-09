import angular from 'angular';
import _ from 'lodash';

class PlatformUsersController {
    constructor(
        $scope, $stateParams,
        modalService, platformService
    ) {
        this.modalService = modalService;
        this.platformService = platformService;
        this.$stateParams = $stateParams;
        this.$scope = $scope;
        // check if has permissions for platform page

        let debouncedSearch = _.debounce(
            this.onSearch.bind(this),
            500,
            {leading: false, trailing: true}
        );

        this.$scope.$watch('$ctrl.search', debouncedSearch);
    }

    onSearch(search) {
        // eslint-disable-next-line
        this.fetchUsers(undefined, search);
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

    fetchUsers(page = 1, search) {
        let platformId = this.$stateParams.platformId;
        this.platformService.getMembers(platformId, page - 1, search).then((response) => {
            this.updatePagination(response);
            this.lastUserResult = response;
            this.users = response.results;

            this.users.forEach(
                (user) => Object.assign(
                    user, {
                        options: {
                            items: this.itemsForUser(user)
                        }
                    }
                ));
        });
    }

    itemsForUser(user) {
        /* eslint-disable */
        return [
            {
                label: 'Edit',
                callback: () => {
                    console.log('edit callback for user:', user);
                }
            }
        ];
        /* eslint-enable */
    }

    newUserModal() {
        this.modalService.open({
            component: 'rfUserModal',
            resolve: { },
            size: 'sm'
        }).result.then((result) => {
            // eslint-disable-next-line
            console.log('user modal closed with value:', result);
        });
    }
}

const PlatformUsersModule = angular.module('pages.platform.users', []);
PlatformUsersModule.controller('PlatformUsersController', PlatformUsersController);

export default PlatformUsersModule;

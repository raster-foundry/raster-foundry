import angular from 'angular';
import _ from 'lodash';

class PlatformUsersController {
    constructor(
        $scope, $stateParams, $q,
        modalService, platformService, authService, paginationService,
        platform
    ) {
        $scope.autoInject(this, arguments);
    }

    $onInit() {
        this.searchTerm = '';
        this.loading = false;
        this.isEffectiveAdmin = this.authService.isEffectiveAdmin(this.platform.id);

        this.debouncedSearch = _.debounce(
            this.onSearch.bind(this),
            500,
            {leading: false, trailing: true}
        );

        this.fetchPage();
    }

    onSearch(search) {
        this.searchTerm = search;
        this.fetchPage();
    }

    fetchPage(page = this.$stateParams.page || 1) {
        this.loading = true;
        this.platformService.getMembers(
            this.platform.id,
            page - 1,
            this.searchTerm
        ).then(paginatedResponse => {
            this.results = paginatedResponse.results;
            this.pagination = this.paginationService.buildPagination(paginatedResponse);
            this.paginationService.updatePageParam(page);
            this.buildOptions();
        }).finally(() => {
            this.loading = false;
        });
    }

    buildOptions() {
        this.results.forEach(user => Object.assign(user, {
            options: {
                items: this.itemsForUser(user)
            },
            showOptions: this.isEffectiveAdmin
        }));
    }

    itemsForUser(user) {
        /* eslint-disable */
        let options = [];

        if (user.groupRole === 'ADMIN') {
            options.push({
                label: 'Revoke admin role',
                callback: () => {
                    this.updateUserGroupRole(Object.assign(user, {
                        groupRole: 'MEMBER'
                    })).then(() => {
                        this.buildOptions();
                    });
                }
            });
        } else {
            options.push({
                label: 'Grant admin role',
                callback: () => {
                    this.updateUserGroupRole(Object.assign(user, {
                        groupRole: 'ADMIN'
                    })).then(() => {
                        this.buildOptions();
                    });
                }
            });
        }
        return options;
        /* eslint-enable */
    }

    updateUserGroupRole(user) {
        return this.platformService.setUserRole(
            this.platform.id,
            user
        ).catch(() => {
            this.fetchUsers(this.pagination.currentPage, this.search);
        });
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

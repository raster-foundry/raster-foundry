import angular from 'angular';
import _ from 'lodash';

class PlatformUsersController {
    constructor(
        $scope, $stateParams, $q,
        modalService, platformService, authService,
        platform
    ) {
        'ngInject';
        this.$scope = $scope;
        this.$stateParams = $stateParams;
        this.$q = $q;

        this.modalService = modalService;
        this.platformService = platformService;
        this.authService = authService;

        this.platform = platform;
    }

    $onInit() {
        this.isEffectiveAdmin = this.authService.isEffectiveAdmin(this.platform.id);
        this.debouncedSearch = _.debounce(
            this.onSearch.bind(this),
            500,
            {leading: false, trailing: true}
        );
        this.fetchUsers(1, '');
    }

    onSearch(search) {
        // eslint-disable-next-line
        this.fetchUsers(undefined, search);
    }

    updateUserGroupRole(user) {
        return this.platformService.setUserRole(
            this.platform.id,
            user
        ).catch(() => {
            this.fetchUsers(this.pagination.currentPage, this.search);
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

    fetchUsers(page = 1, search) {
        let platformId = this.$stateParams.platformId;
        this.fetching = true;
        this.platformService.getMembers(
            platformId,
            page - 1,
            search && search.length ? search : null
        ).then((response) => {
            this.fetching = false;
            this.updatePagination(response);
            this.lastUserResult = response;
            this.users = response.results;
            this.buildOptions();
        }, (error) => {
            this.fetching = false;
            this.errrorMsg = `${error.data}. Please contact `;
        });
    }

    buildOptions() {
        this.users.forEach(user => Object.assign(user, {
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

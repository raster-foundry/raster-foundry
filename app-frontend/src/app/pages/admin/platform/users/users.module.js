import angular from 'angular';
import _ from 'lodash';

class PlatformUsersController {
    constructor(
        $scope, $stateParams, $q,
        modalService, platformService
    ) {
        'ngInject';
        this.$scope = $scope;
        this.$stateParams = $stateParams;
        this.$q = $q;

        this.modalService = modalService;
        this.platformService = platformService;
        // check if has permissions for platform page
        this.fetching = true;

        this.debouncedSearch = _.debounce(
            this.onSearch.bind(this),
            500,
            {leading: false, trailing: true}
        );

    }

    $onInit() {
        this.platformPromise = this.$scope.$parent.$ctrl.platformPromise;
        this.currentUserPromise = this.$scope.$parent.$ctrl.currentUserPromise;
        this.currentUgrPromise = this.$scope.$parent.$ctrl.currentUgrPromise;
        this.fetchUsers(1, '');
        this.getUserAndUgrs();
    }

    onSearch(search) {
        // eslint-disable-next-line
        this.fetchUsers(undefined, search);
    }

    getUserAndUgrs() {
        this.currentUserPromise.then(resp => {
            this.currentUser = resp;
        });
        this.$q.all({
            ugrs: this.currentUgrPromise,
            platform: this.platformPromise
        }).then(({ugrs, platform}) => {
            this.currentPlatUgr = ugrs.find((ugr) => {
                return ugr.groupId === platform.id && ugr.groupRole === 'ADMIN';
            });
        });
    }

    updateUserGroupRole(user) {
        this.platformPromise.then(platform => {
            this.platformService.setUserRole(
                platform.id,
                user
            ).catch(() => {
                this.fetchUsers(this.pagination.currentPage, this.search);
            });
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

            this.users.forEach(user => {
                Object.assign(user, {
                    options: {
                        items: this.itemsForUser(user)
                    }
                });
            });
        }, (error) => {
            this.fetching = false;
            this.errrorMsg = `${error.data}. Please contact `;
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

import angular from 'angular';
import _ from 'lodash';

class OrganizationUsersController {
    constructor(
        $scope, $stateParams,
        modalService, organizationService
    ) {
        this.$scope = $scope;
        this.$stateParams = $stateParams;
        this.modalService = modalService;
        this.organizationService = organizationService;
        this.fetching = true;

        this.platAdminEmail = 'example@email.com';

        this.debouncedSearch = _.debounce(
            this.onSearch.bind(this),
            500,
            {leading: false, trailing: true}
        );

        this.orgWatch = this.$scope.$parent.$watch('$ctrl.organization', (organization) => {
            if (organization && this.orgWatch) {
                this.orgWatch();
                delete this.orgWatch;
                this.organization = organization;
                this.organizationId = this.organization.id;
                this.currentUserPromise = this.$scope.$parent.$ctrl.currentUserPromise;
                this.currentUgrPromise = this.$scope.$parent.$ctrl.currentUgrPromise;
                this.getUserAndUgrs();
                this.fetchUsers(1, '');
            }
        });
    }

    getUserAndUgrs() {
        this.currentUserPromise.then(resp => {
            this.currentUser = resp;
        });
        this.currentUgrPromise.then((resp) => {
            this.currentOrgUgr = resp.find((ugr) => {
                return ugr.groupId === this.organizationId;
            });
            this.currentPlatUgr = resp.find((ugr) => {
                return ugr.groupId === this.organization.platformId;
            });
        });
    }

    onSearch(search) {
        this.fetchUsers(1, search);
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

    updateUserGroupRole(user) {
        this.organizationService.setUserRole(
            this.organization.platformId,
            this.organization.id,
            user
        ).catch(() => {
            this.fetchUsers(this.pagination.currentPage, this.search);
        });
    }

    fetchUsers(page = 1, search) {
        const platformId = this.organization.platformId;
        const organizationId = this.organization.id;
        this.fetching = true;
        this.organizationService
            .getMembers(platformId, organizationId, page - 1, search)
            .then((response) => {
                this.fetching = false;
                this.updatePagination(response);
                this.lastUserResult = response;
                this.users = response.results;

                let isAdmin = this.currentPlatUgr && this.currentPlatUgr.groupRole === 'ADMIN' ||
                    this.currentOrgUgr && this.currentOrgUgr.groupRole === 'ADMIN';

                this.users.forEach(user => Object.assign(user, {
                    options: {
                        items: this.itemsForUser(user)
                    },
                    showOptions: user.isActive && (user.id === this.currentUser.id ||
                        user.isSuperuser || isAdmin)
                }));
            }, (error) => {
                this.fetching = false;
                this.errorMsg = `${error.data}. Please contact `;
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
            },
            {
                label: 'Delete',
                callback: () => {
                    console.log('delete callback for user:', user);
                },
                classes: ['color-danger']
            }
        ];
        /* eslint-enable */
    }

    addUserModal() {
        this.modalService.open({
            component: 'rfAddUserModal',
            resolve: {
                platformId: () => this.organization.platformId,
                organizationId: () => this.organization.id,
                adminView: () => 'organization'
            }
        }).result.then(() => {
            this.fetchUsers(1, this.search);
        });
    }
}

const OrganizationUsersModule = angular.module('pages.organization.users', []);
OrganizationUsersModule.controller('OrganizationUsersController', OrganizationUsersController);

export default OrganizationUsersModule;

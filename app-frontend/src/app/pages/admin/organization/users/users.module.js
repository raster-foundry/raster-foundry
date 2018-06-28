import angular from 'angular';
import _ from 'lodash';

class OrganizationUsersController {
    constructor(
        $scope, $stateParams,
        modalService, organizationService, authService,
        // params from parent route resolve
        organization, platform, user
    ) {
        this.$scope = $scope;
        this.$stateParams = $stateParams;
        this.modalService = modalService;
        this.organizationService = organizationService;
        this.authService = authService;

        this.organization = organization;
        this.platform = platform;
        this.user = user;
    }

    $onInit() {
        this.debouncedSearch = _.debounce(
            this.onSearch.bind(this),
            500,
            {leading: false, trailing: true}
        );

        this.isEffectiveAdmin = this.authService.isEffectiveAdmin([
            this.platform.id,
            this.organization.id
        ]);

        this.fetchUsers(1, '');
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
        return this.organizationService.setUserRole(
            this.organization.platformId,
            this.organization.id,
            user
        ).catch(() => {
            this.fetchUsers(this.pagination.currentPage, this.search);
        });
    }

    fetchUsers(page = 1, search) {
        this.fetching = true;
        this.organizationService
            .getMembers(this.platform.id, this.organization.id, page - 1, search)
            .then((response) => {
                this.fetching = false;
                this.updatePagination(response);
                this.lastUserResult = response;
                this.users = response.results;
                this.buildOptions();
            }, () => {
                this.fetching = false;
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

    addUserModal() {
        this.modalService.open({
            component: 'rfAddUserModal',
            resolve: {
                platformId: () => this.organization.platformId,
                organizationId: () => this.organization.id,
                groupType: () => 'organization'
            }
        }).result.then(() => {
            this.fetchUsers(1, this.search);
        });
    }

    getUserGroupRoleLabel(user) {
        switch (user.membershipStatus) {
        case 'INVITED':
            return 'Pending invitation';
        case 'REQUESTED':
            return 'Pending approval';
        default:
            return user.groupRole;
        }
    }

    updateUserMembershipStatus(user, isApproved) {
        if (isApproved) {
            this.organizationService.approveUserMembership(
                this.organization.platformId,
                this.organization.id,
                user.id,
                user.groupRole
            ).then(resp => {
                this.users.forEach(thisUser =>{
                    if (thisUser.id === resp.userId) {
                        thisUser.membershipStatus = resp.membershipStatus;
                        delete thisUser.buttonType;
                    }
                });
                this.fetchUsers(1, '');
            });
        } else {
            this.organizationService.removeUser(
                this.organization.platformId,
                this.organization.id,
                user.id
            ).then(resp => {
                _.remove(this.users, thisUser => thisUser.id === resp[0].userId);
                this.fetchUsers(1, '');
            });
        }
    }
}

const OrganizationUsersModule = angular.module('pages.organization.users', []);
OrganizationUsersModule.controller('OrganizationUsersController', OrganizationUsersController);

export default OrganizationUsersModule;

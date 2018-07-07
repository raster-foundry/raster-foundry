import angular from 'angular';
import _ from 'lodash';

class OrganizationUsersController {
    constructor(
        $scope, $stateParams,
        modalService, organizationService, authService, paginationService,
        // params from parent route resolve
        organization, platform, user
    ) {
        'ngInject';
        $scope.autoInject(this, arguments);
    }

    $onInit() {
        this.searchTerm = '';
        this.loading = false;
        this.onSearch = this.paginationService.buildPagedSearch(this);

        this.isEffectiveAdmin = this.authService.isEffectiveAdmin([
            this.platform.id,
            this.organization.id
        ]);

        this.fetchPage();
    }

    fetchPage(page = this.$stateParams.page || 1) {
        this.loading = true;
        this.organizationService
            .getMembers(this.platform.id, this.organization.id, page - 1, this.searchTerm)
            .then(paginatedResponse => {
                this.results = paginatedResponse.results;
                this.pagination = this.paginationService.buildPagination(paginatedResponse);
                this.paginationService.updatePageParam(page);
            }).finally(() => {
                this.loading = false;
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

    updateUserGroupRole(user) {
        return this.organizationService.setUserRole(
            this.organization.platformId,
            this.organization.id,
            user
        ).catch(() => {
            this.fetchUsers(this.pagination.currentPage);
        });
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

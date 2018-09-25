import angular from 'angular';
import _ from 'lodash';

class OrganizationUsersController {
    constructor(
        $scope, $state,
        modalService, organizationService, authService, paginationService,
        // params from parent route resolve
        organization, platform, user
    ) {
        'ngInject';
        $scope.autoInject(this, arguments);
    }

    $onInit() {
        this.isEffectiveAdmin = this.authService.isEffectiveAdmin([
            this.platform.id,
            this.organization.id
        ]);

        this.fetchPage();
    }

    fetchPage(page = this.$state.params.page || 1, search = this.$state.params.search) {
        this.search = search && search.length ? search : null;
        delete this.fetchError;
        this.results = [];
        let currentQuery = this.organizationService
            .getMembers(this.platform.id, this.organization.id, page - 1, this.search)
            .then(paginatedResponse => {
                this.results = paginatedResponse.results;
                this.pagination = this.paginationService.buildPagination(paginatedResponse);
                this.paginationService.updatePageParam(page, this.search);
                this.buildOptions();
                if (this.currentQuery === currentQuery) {
                    delete this.fetchError;
                }
            }, (e) => {
                if (this.currentQuery === currentQuery) {
                    this.fetchError = e;
                }
            }).finally(() => {
                if (this.currentQuery === currentQuery) {
                    delete this.currentQuery;
                }
            });
        this.currentQuery = currentQuery;
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
            }, {
                label: 'Remove user',
                callback: () => {
                    this.organizationService.removeUser(
                        this.platform.id, this.organization.id, user.id
                    ).then(() => {
                        return this.fetchPage();
                    });
                }
            }
            );
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
            this.fetchPage();
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
            this.fetchPage(this.pagination.currentPage);
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
                this.results.forEach(thisUser =>{
                    if (thisUser.id === resp.userId) {
                        thisUser.membershipStatus = resp.membershipStatus;
                        delete thisUser.buttonType;
                    }
                });
                this.fetchPage();
            });
        } else {
            this.organizationService.removeUser(
                this.organization.platformId,
                this.organization.id,
                user.id
            ).then(resp => {
                _.remove(this.results, thisUser => thisUser.id === resp[0].userId);
                this.fetchPage();
            });
        }
    }
}

const OrganizationUsersModule = angular.module('pages.organization.users', []);
OrganizationUsersModule.controller('OrganizationUsersController', OrganizationUsersController);

export default OrganizationUsersModule;

import angular from 'angular';
import _ from 'lodash';

class TeamUsersController {
    constructor(
      $scope, $state,
      teamService, modalService, authService, paginationService,
      platform, organization, team
    ) {
        'ngInject';
        $scope.autoInject(this, arguments);
    }

    $onInit() {
        this.isEffectiveAdmin = this.authService.isEffectiveAdmin([
            this.platform.id,
            this.organization.id,
            this.team.id
        ]);

        this.fetchPage();
    }

    updateUserGroupRole(user) {
        return this.teamService.setUserRole(
            this.platform.id,
            this.organization.id,
            this.team.id,
            user
        ).catch(() => {
            this.fetchPage();
        });
    }

    fetchPage(page = this.$state.params.page || 1, search = this.$state.params.search) {
        this.search = search && search.length ? search : null;
        delete this.fetchError;
        this.results = [];
        const currentQuery = this.teamService.getMembers(
            this.platform.id,
            this.organization.id,
            this.team.id,
            page - 1,
            this.search
        ).then(paginatedResponse => {
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
            });
        }

        options = options.concat([{
            classes: 'divider'
        },{
            label: 'Remove',
            callback: () => {
                this.removeUser(user);
            }
        }]);

        return options;
        /* eslint-enable */
    }

    addUser() {
        this.modalService.open({
            component: 'rfAddUserModal',
            resolve: {
                platformId: () => this.platform.id,
                organizationId: () => this.organization.id,
                teamId: () => this.team.id,
                groupType: () => 'team'
            }
        }).result.then(() => {
            this.fetchPage();
        });
    }

    removeUser(user) {
        const modal = this.modalService.open({
            component: 'rfConfirmationModal',
            resolve: {
                title: () => `Remove ${user.name || user.email || user.id} from this team?`,
                confirmText: () => 'Remove User',
                cancelText: () => 'Cancel'
            }
        });

        modal.result.then(() => {
            this.teamService.removeUser(
                this.platform.id,
                this.organization.id,
                this.team.id,
                user.id
            ).then(() => {
                this.fetchPage(this.pagination.currentPage);
            });
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
            this.teamService.setUserRole(
                this.platform.id,
                this.organization.id,
                this.team.id,
                user
            ).then(resp => {
                this.results.forEach(thisUser =>{
                    if (thisUser.id === resp.userId) {
                        thisUser.membershipStatus = resp.membershipStatus;
                    }
                });
                this.fetchPage(this.pagination.currentPage, '');
            });
        } else {
            this.teamService.removeUser(
                this.platform.id,
                this.organization.id,
                this.team.id,
                user.id
            ).then(resp => {
                _.remove(this.results, thisUser => thisUser.id === resp[0].userId);
                this.fetchPage(this.pagination.currentPage);
            });
        }
    }
}

const TeamUsersModule = angular.module('pages.admin.team.users', []);

TeamUsersModule.controller('AdminTeamUsersController', TeamUsersController);

export default TeamUsersModule;

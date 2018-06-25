import angular from 'angular';
import _ from 'lodash';

class TeamUsersController {
    constructor(
      $scope,
      teamService, modalService, authService,
      platform, organization, team
    ) {
        'ngInject';
        this.$scope = $scope;

        this.teamService = teamService;
        this.modalService = modalService;
        this.authService = authService;
        this.platform = platform;
        this.organization = organization;
        this.team = team;
        this.searchTerm = false;
    }

    $onInit() {
        this.platformId = this.organization.platformId;

        this.debouncedSearch = _.debounce(
            this.onSearch.bind(this),
            500,
            {leading: false, trailing: true}
        );

        this.isEffectiveAdmin = this.authService.isEffectiveAdmin([
            this.platform.id,
            this.organization.id,
            this.team.id
        ]);

        this.fetchUsers(1, '');
    }

    updateUserGroupRole(user) {
        return this.teamService.setUserRole(
            this.organization.platformId,
            this.organization.id,
            this.team.id,
            user
        ).catch(() => {
            this.fetchUsers(this.pagination.currentPage, this.search);
        });
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
        this.fetching = true;
        this.searchTerm = search;
        this.teamService.getMembers(
            this.platformId,
            this.organization.id,
            this.team.id,
            page - 1,
            search && search.length ? search : null
        ).then((response) => {
            this.fetching = false;
            this.updatePagination(response);
            this.lastUserResult = response;
            this.users = response.results;
            this.buildOptions();
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
                platformId: () => this.platformId,
                organizationId: () => this.organization.id,
                teamId: () => this.team.id,
                groupType: () => 'team'
            }
        }).result.then(() => {
            this.fetchUsers(1, this.search);
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
                this.platformId,
                this.organization.id,
                this.team.id,
                user.id
            ).then(() => {
                this.fetchUsers(this.pagination.currentPage, this.search);
            });
        });
    }
}

const TeamUsersModule = angular.module('pages.admin.team.users', []);

TeamUsersModule.controller('AdminTeamUsersController', TeamUsersController);

export default TeamUsersModule;

import angular from 'angular';
import _ from 'lodash';

class OrganizationTeamsController {
    constructor(
        $scope, $stateParams, $log,
        modalService, organizationService, teamService, authService
    ) {
        this.$scope = $scope;
        this.$stateParams = $stateParams;
        this.$log = $log;
        this.modalService = modalService;
        this.organizationService = organizationService;
        this.teamService = teamService;
        this.authService = authService;

        let debouncedSearch = _.debounce(
            this.onSearch.bind(this),
            500,
            {leading: false, trailing: true}
        );
        this.fetching = true;
        this.orgWatch = this.$scope.$parent.$watch('$ctrl.organization', (organization) => {
            if (organization && this.orgWatch) {
                this.orgWatch();
                delete this.orgWatch;
                this.organization = organization;
                this.organizationId = this.organization.id;
                this.platformId = this.organization.platformId;
                this.$scope.$watch('$ctrl.search', debouncedSearch);
            }
        });
    }

    $onInit() {
        this.authService.getCurrentUser().then(resp => {
            this.currentUser = resp;
        });
        this.authService.fetchUserRoles().then((resp) => {
            this.currentUgr = resp.filter(ugr => ugr.groupId === this.organizationId)[0];
        }, (err) => {
            this.$log.error(err);
        });
    }

    onSearch(search) {
        this.fetchTeams(1, search);
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


    fetchTeams(page = 1, search) {
        this.fetching = true;
        this.organizationService
            .getTeams(this.platformId, this.organizationId, page - 1, search)
            .then((response) => {
                this.fetching = false;
                this.updatePagination(response);
                this.lastTeamResult = response;
                this.teams = response.results;

                this.teams.forEach(
                    (team) => Object.assign(
                        team, {
                            options: {
                                items: this.itemsForTeam(team)
                            }
                        }
                    ));

                // fetch team users
                this.teams.forEach(
                    (team) => {
                        this.teamService
                            .getMembers(this.platformId, this.organizationId, team.id)
                            .then((paginatedUsers) => {
                                team.fetchedUsers = paginatedUsers;
                            });
                    }
                );
            });
    }


    itemsForTeam(team) {
        /* eslint-disable */
        return [
            {
                label: 'Edit',
                callback: () => {
                    this.$state.go('admin.team.users', {teamId: team.id});
                },
                classes: []
            },
            {
                label: 'Add to team...',
                callback: () => {
                    this.modalService.open({
                        component: 'rfAddUserModal',
                        resolve: {
                            platformId: () => this.platformId,
                            organizationId: () => this.organizationId,
                            teamId: () => team.id
                        }
                    }).result.then(() => {
                        this.teamService
                            .getMembers(this.platformId, this.organization.id, team.id)
                            .then((paginatedUsers) => {
                                team.fetchedUsers = paginatedUsers;
                            });
                    });
                },
                classes: []
            },
            {
                label: 'Delete',
                callback: () => {
                    const modal = this.modalService.open({
                        component: 'rfConfirmationModal',
                        resolve: {
                            title: () => 'Delete team?',
                            content: () => 'This action is not reversible. Anything shared with this team will' +
                                ' no longer be accessible by its members.',
                            confirmText: () => 'Delete Team',
                            cancelText: () => 'Cancel'
                        }
                    });

                    modal.result.then(() => {
                        this.teamService.deactivateTeam(this.platformId, this.organizationId, team.id).then(
                            () => {
                                this.fetchTeams(this.pagination.currentPage, this.search);
                            },
                            (err) => {
                                this.$log.debug('error deleting team', err);
                                this.fetchTeams(this.pagination.currentPage, this.search);
                            }
                        );
                    });
                },
                classes: ['color-danger']
            }
        ];
        /* eslint-enable */
    }

    newTeamModal() {
        let permissionDenied = {};
        this.$log.log(!(this.currentUser.isActive &&
            (this.currentUser.isSuperuser || this.currentUgr.groupRole === 'ADMIN')));
        if (!(this.currentUser.isActive &&
            (this.currentUser.isSuperuser || this.currentUgr.groupRole === 'ADMIN'))) {
            permissionDenied = {
                isDenied: true,
                adminEmail: 'example@email.com',
                message: 'You do not have access to this operation. Please contact ',
                subject: 'organization admin'
            };
        }
        this.modalService.open({
            component: 'rfTeamModal',
            resolve: {
                permissionDenied: permissionDenied
            },
            size: 'sm'
        }).result.then((result) => {
            // eslint-disable-next-line
            this.teamService.createTeam(this.platformId, this.organizationId, result.name).then(() => {
                this.fetchTeams(this.pagination.currentPage, this.search);
            });
        });
    }
}

const OrganizationTeamsModule = angular.module('pages.organization.teams', []);
OrganizationTeamsModule.controller('OrganizationTeamsController', OrganizationTeamsController);

export default OrganizationTeamsModule;

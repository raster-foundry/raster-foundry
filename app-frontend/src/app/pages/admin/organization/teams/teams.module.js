import angular from 'angular';
import _ from 'lodash';

class OrganizationTeamsController {
    constructor(
        $scope, $stateParams, $log, $window,
        modalService, organizationService, teamService, authService,
        platform, organization, user, userRoles
    ) {
        this.$scope = $scope;
        this.$stateParams = $stateParams;
        this.$log = $log;
        this.$window = $window;
        this.modalService = modalService;
        this.organizationService = organizationService;
        this.teamService = teamService;
        this.authService = authService;

        this.platform = platform;
        this.organization = organization;
        this.user = user;
        this.userRoles = userRoles;
    }

    $onInit() {
        this.debouncedSearch = _.debounce(
            this.onSearch.bind(this),
            500,
            {leading: false, trailing: true}
        );

        this.isEffectiveAdmin = this.authService.isEffectiveAdmin([
            this.organization.id,
            this.platform.id
        ]);

        this.fetchTeams(1, '');
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
            .getTeams(this.platform.id, this.organization.id, page - 1, search)
            .then((response) => {
                this.fetching = false;
                this.updatePagination(response);
                this.lastTeamResult = response;
                this.teams = response.results;

                this.teams.forEach((team) => {
                    Object.assign(team, {
                        options: {
                            items: this.itemsForTeam(team)
                        },
                        showOptions: this.authService.isEffectiveAdmin([
                            this.platform.id,
                            this.organization.id,
                            team.id
                        ])
                    });
                });

                // fetch team users
                this.teams.forEach(
                    (team) => {
                        this.teamService
                            .getMembers(this.platform.id, this.organization.id, team.id)
                            .then((paginatedUsers) => {
                                team.fetchedUsers = paginatedUsers;
                            });
                    }
                );
            }, (error) => {
                this.fetching = false;
                this.errorMsg = `${error.data}. Please contact `;
            });
    }


    itemsForTeam(team) {
        /* eslint-disable */
        return [
            // {
            //     label: 'Edit',
            //     callback: () => {
            //         this.$state.go('admin.team.users', {teamId: team.id});
            //     },
            //     classes: []
            // },
            {
                label: 'Add User',
                callback: () => {
                    this.modalService.open({
                        component: 'rfAddUserModal',
                        resolve: {
                            platformId: () => this.organization.platformId,
                            organizationId: () => this.organization.id,
                            teamId: () => team.id,
                            groupType: () => 'team'
                        }
                    }).result.then(() => {
                        this.teamService
                            .getMembers(this.platform.id, this.organization.id, team.id)
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
                        this.teamService.deactivateTeam(this.platform.id, this.organization.id, team.id).then(
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
        this.modalService.open({
            component: 'rfTeamModal',
            size: 'sm'
        }).result.then((result) => {
            // eslint-disable-next-line
            this.teamService
                .createTeam(this.platform.id, this.organization.id, result.name)
                .then(() => {
                    this.fetchTeams(this.pagination.currentPage, this.search);
                });
        });
    }

    toggleTeamNameEdit(teamId, isEdit) {
        this.nameBuffer = '';
        this.editTeamId = isEdit ? teamId : null;
        this.isEditOrgName = isEdit;
    }

    finishTeamNameEdit(team) {
        if (this.nameBuffer && this.nameBuffer.length && team.name !== this.nameBuffer) {
            let teamUpdated = Object.assign({}, team, {name: this.nameBuffer});
            this.teamService
                .updateTeam(this.platform.id, this.organization.id, team.id, teamUpdated)
                .then(resp => {
                    this.teams[this.teams.indexOf(team)] = resp;
                }, () => {
                    this.$window.alert('Team\'s name cannot be updated at the moment.');
                }).finally(() => {
                    delete this.editTeamId;
                    delete this.isEditTeamName;
                    this.nameBuffer = '';
                });
        } else {
            delete this.editTeamId;
            delete this.isEditTeamName;
            this.nameBuffer = '';
        }
    }

    getInitialNameBuffer(teamId) {
        let team = this.teams.find(t => t.id === teamId);
        return team ? team.name : '';
    }
}

const OrganizationTeamsModule = angular.module('pages.organization.teams', []);
OrganizationTeamsModule.controller('OrganizationTeamsController', OrganizationTeamsController);

export default OrganizationTeamsModule;

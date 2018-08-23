import angular from 'angular';
import _ from 'lodash';

class OrganizationTeamsController {
    constructor(
        $scope, $state, $log, $window,
        modalService, organizationService, teamService, authService, paginationService,
        platform, organization, user, userRoles
    ) {
        'ngInject';
        $scope.autoInject(this, arguments);
    }

    $onInit() {
        this.isEffectiveAdmin = this.authService.isEffectiveAdmin([
            this.organization.id,
            this.platform.id
        ]);

        this.fetchPage();
    }

    fetchPage(page = this.$state.params.page || 1, search = this.$state.params.search) {
        this.search = search && search.length ? search : null;
        delete this.fetchError;
        this.results = [];
        const currentQuery = this.organizationService
            .getTeams(this.platform.id, this.organization.id, page - 1, this.search)
            .then(paginatedResponse => {
                this.results = paginatedResponse.results;
                this.pagination = this.paginationService.buildPagination(paginatedResponse);
                this.paginationService.updatePageParam(page, this.search);
                this.buildOptions();
                this.fetchTeamUsers();
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
        this.results.forEach((team) => {
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
    }

    fetchTeamUsers() {
        this.results.forEach(team => {
            this.teamService
                .getMembers(this.platform.id, this.organization.id, team.id)
                .then((paginatedUsers) => {
                    team.fetchedUsers = paginatedUsers;
                });
        });
    }


    itemsForTeam(team) {
        /* eslint-disable */
        return [
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
                classes: ['divider']
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
                                this.fetchPage(this.pagination.currentPage);
                            },
                            (err) => {
                                this.$log.debug('error deleting team', err);
                                this.fetchPage(this.pagination.currentPage);
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
                    this.fetchPage(this.pagination.currentPage);
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

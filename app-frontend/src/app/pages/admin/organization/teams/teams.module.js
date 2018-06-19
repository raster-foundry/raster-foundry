import angular from 'angular';
import _ from 'lodash';

class OrganizationTeamsController {
    constructor(
        $scope, $stateParams, $log, $window,
        modalService, organizationService, teamService, authService
    ) {
        this.$scope = $scope;
        this.$stateParams = $stateParams;
        this.$log = $log;
        this.$window = $window;
        this.modalService = modalService;
        this.organizationService = organizationService;
        this.teamService = teamService;
        this.authService = authService;

        this.orgAdminEmail = 'example@email.com';

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
                this.currentUserPromise = this.$scope.$parent.$ctrl.currentUserPromise;
                this.currentUgrPromise = this.$scope.$parent.$ctrl.currentUgrPromise;
                this.getUserAndUgrs();
                this.$scope.$watch('$ctrl.search', debouncedSearch);
            }
        });
    }

    $onInit() {
        this.userTeamRole = {};
    }

    getUserAndUgrs() {
        this.currentUserPromise.then(resp => {
            this.currentUser = resp;
        });
        this.currentUgrPromise.then((resp) => {
            this.currentUgrs = resp;
            this.currentOrgUgr = resp.filter((ugr) => {
                return ugr.groupId === this.organizationId;
            })[0];
            this.currentPlatUgr = resp.filter((ugr) => {
                return ugr.groupId === this.organization.platformId;
            })[0];

            this.isPlatOrOrgAdmin = this.currentPlatUgr &&
                this.currentPlatUgr.groupRole === 'ADMIN' ||
                this.currentOrgUgr && this.currentOrgUgr.groupRole === 'ADMIN';
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

                this.teams.forEach((team) => {
                    let teamUgr = this.currentUgrs.filter(ugr => ugr.groupId === team.id)[0];
                    let isAdmin = this.isPlatOrOrgAdmin || teamUgr && teamUgr.groupRole === 'ADMIN';
                    this.userTeamRole[team.id] = this.currentUser.isSuperuser || isAdmin;
                    Object.assign(team, {
                        options: {
                            items: this.itemsForTeam(team)
                        },
                        showOptions: this.userTeamRole[team.id]
                    });
                });

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
                            adminView: () => 'organization'
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
        if (!(this.currentUser.isActive &&
            (this.currentUser.isSuperuser || this.isPlatOrOrgAdmin))) {
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

    toggleTeamNameEdit(teamId, isEdit) {
        this.nameBuffer = '';
        this.editTeamId = isEdit ? teamId : null;
        this.isEditOrgName = isEdit;
    }

    finishTeamNameEdit(team) {
        if (this.nameBuffer && this.nameBuffer.length && team.name !== this.nameBuffer) {
            let teamUpdated = Object.assign({}, team, {name: this.nameBuffer});
            this.teamService
                .updateTeam(this.platformId, this.organizationId, team.id, teamUpdated)
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

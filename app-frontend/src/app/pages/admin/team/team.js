import angular from 'angular';

class TeamController {
    constructor($q, $stateParams, teamService, organizationService, authService) {
        'ngInject';
        this.$q = $q;
        this.$stateParams = $stateParams;
        this.teamService = teamService;
        this.organizationService = organizationService;
        this.fetching = true;
        this.authService = authService;
    }

    $onInit() {
        this.teamPromise = this.$q((resolve, reject) => {
            this.teamService
                .getTeam(this.$stateParams.teamId)
                .then((team) => {
                    this.fetching = false;
                    this.team = team;
                    return this.organizationService
                        .getOrganization(team.organizationId)
                        .then((organization) => {
                            resolve({
                                team, organization
                            });
                        }, reject);
                }, reject);
        });

        this.isSuperOrAdmin = this.isUserSuperOrAdmin();
    }

    isUserSuperOrAdmin() {
        return this.teamPromise.then(resp => {
            this.platformId = resp.organization.platformId;
            this.organizationId = resp.organization.id;
            this.isSuperOrAdmin = this.authService.isSuperOrAdmin(
                [this.platformId,
                this.organizationId,
                this.$stateParams.teamId]
            );
        });
    }

    toggleTeamNameEdit() {
        this.isEditTeamName = !this.isEditTeamName;
        delete this.nameBuffer;
    }

    finishTeamNameEdit() {
        if (this.nameBuffer && this.nameBuffer.length
            && this.nameBuffer !== this.team.name) {
            let teamUpdated = Object.assign({}, this.team, {name: this.nameBuffer});
            this.teamService.updateTeam(
                this.platformId,
                this.organizationId,
                this.$stateParams.teamId,
                teamUpdated)
            .then(resp => {
                this.team = resp;
            });
        }
        delete this.nameBuffer;
        delete this.isEditTeamName;
    }
}

const TeamModule = angular.module('pages.admin.team', []);
TeamModule.controller('AdminTeamController', TeamController);

export default TeamModule;

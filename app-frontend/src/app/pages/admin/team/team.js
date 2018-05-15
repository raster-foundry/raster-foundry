import angular from 'angular';

class TeamController {
    constructor($q, $stateParams, teamService, organizationService) {
        'ngInject';
        this.$q = $q;
        this.$stateParams = $stateParams;
        this.teamService = teamService;
        this.organizationService = organizationService;
        this.fetching = true;
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
    }
}

const TeamModule = angular.module('pages.admin.team', []);
TeamModule.controller('AdminTeamController', TeamController);

export default TeamModule;

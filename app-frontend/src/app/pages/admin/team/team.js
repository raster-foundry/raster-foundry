import angular from 'angular';

class TeamController {
    constructor(
        authService, teamService,
        platform, organization, team, users
    ) {
        'ngInject';
        this.authService = authService;
        this.teamService = teamService;
        this.platform = platform;
        this.organization = organization;
        this.team = team;
        this.users = users;
    }
    $onInit() {
        this.isEffectiveAdmin = this.authService.isEffectiveAdmin([
            this.platform.id,
            this.organization.id,
            this.team.id
        ]);
    }

    toggleTeamNameEdit() {
        this.isEditTeamName = !this.isEditTeamName;
    }

    finishTeamNameEdit() {
        if (this.nameBuffer && this.nameBuffer.length
            && this.nameBuffer !== this.team.name) {
            let teamUpdated = Object.assign({}, this.team, {name: this.nameBuffer});
            this.teamService.updateTeam(
                this.platform.id,
                this.organization.id,
                this.team.id,
                teamUpdated
            ).then(resp => {
                this.team = resp;
                this.nameBuffer = this.team.name;
            }, () => {
                this.$window.alert('Team\'s name cannot be updated at the moment.');
                delete this.nameBuffer;
            }).finally(() => {
                delete this.isEditTeamName;
            });
        } else {
            delete this.nameBuffer;
            delete this.isEditTeamName;
        }
    }
}

const TeamModule = angular.module('pages.admin.team', []);

TeamModule.resolve = {
    team: ($stateParams, teamService) => {
        return teamService.getTeam($stateParams.teamId);
    },
    organization: (team, organizationService) => {
        return organizationService.getOrganization(team.organizationId);
    },
    platform: (organization, platformService) => {
        return platformService.getPlatform(organization.platformId);
    },
    user: (authService) => {
        return authService.getCurrentUser();
    },
    userRoles: (authService) => {
        return authService.fetchUserRoles();
    },
    users: (platform, organization, team, teamService) => {
        return teamService.getMembers(platform.id, organization.id, team.id);
    }
};

TeamModule.controller('AdminTeamController', TeamController);

export default TeamModule;

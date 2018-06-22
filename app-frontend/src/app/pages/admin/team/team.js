import angular from 'angular';

class TeamController {
    constructor(
        authService, teamService,
        platform, team, organization, members,
        projects, rasters, vectors, datasources, templates, analyses
    ) {
        'ngInject';
        this.authService = authService;
        this.teamService = teamService;
        this.platform = platform;
        this.team = team;
        this.organization = organization;
        this.members = members;
        this.projects = projects;
        this.rasters = rasters;
        this.vectors = vectors;
        this.datasources = datasources;
        this.templates = templates;
        this.analyses = analyses;
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
    members: (platform, team, organization, teamService) => {
        return teamService.getMembers(platform.id, organization.id, team.id);
    },
    projects: (team, projectService) => {
        return projectService.query(
            {
                sort: 'createdAt,desc',
                pageSize: 10,
                page: 1,
                ownershipType: 'inherited',
                groupType: 'team',
                groupId: team.id
            }
        );
    },
    rasters: (team, sceneService) => {
        return sceneService.query(
            {
                sort: 'createdAt,desc',
                pageSize: 10,
                page: 1,
                ownershipType: 'inherited',
                groupType: 'team',
                groupId: team.id
            }
        );
    },
    vectors: (team, shapesService) => {
        return shapesService.query(
            {
                sort: 'createdAt,desc',
                pageSize: 10,
                page: 1,
                ownershipType: 'inherited',
                groupType: 'team',
                groupId: team.id
            }
        );
    },
    datasources: (team, datasourceService) => {
        return datasourceService.query(
            {
                sort: 'createdAt,desc',
                pageSize: 10,
                page: 1,
                ownershipType: 'inherited',
                groupType: 'team',
                groupId: team.id
            }
        );
    },
    templates: (team, analysisService) => {
        return analysisService.fetchTemplates(
            {
                sort: 'createdAt,desc',
                pageSize: 10,
                page: 1,
                ownershipType: 'inherited',
                groupType: 'team',
                groupId: team.id
            }
        );
    },
    analyses: (team, analysisService) => {
        return analysisService.fetchAnalyses(
            {
                sort: 'createdAt,desc',
                pageSize: 10,
                page: 1,
                ownershipType: 'inherited',
                groupType: 'team',
                groupId: team.id
            }
        );
    }
};

TeamModule.controller('AdminTeamController', TeamController);

export default TeamModule;

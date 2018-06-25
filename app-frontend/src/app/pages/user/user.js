/* global _ */
import angular from 'angular';

class UserController {
    constructor(
        authService, teamService,
        user, userRoles, organizations, teams,
        projects, rasters, vectors, datasources, templates, analyses
    ) {
        'ngInject';
        this.authService = authService;
        this.teamService = teamService;
        this.user = user;
        this.userRoles = userRoles;
        this.organizations = organizations;
        this.teams = teams;
        this.projects = projects;
        this.rasters = rasters;
        this.vectors = vectors;
        this.datasources = datasources;
        this.templates = templates;
        this.analyses = analyses;
    }
}

const UserModule = angular.module('pages.user', []);

UserModule.resolve = {
    user: ($stateParams, authService) => {
        if ($stateParams.userId === 'me') {
            return authService.getCurrentUser();
        }
        return false;
    },
    userRoles: (authService) => {
        return authService.fetchUserRoles();
    },
    organizations: ($q, userRoles, organizationService) => {
        const promises =
            _.uniqBy(userRoles, r => r.groupId)
            .filter(r => r.groupType === 'ORGANIZATION')
            .map(r => organizationService.getOrganization(r.groupId));

        return $q.all(promises);
    },
    teams: ($q, userRoles, teamService) => {
        const promises =
            _.uniqBy(userRoles, r => r.groupId)
            .filter(r => r.groupType === 'TEAM')
            .map(r => teamService.getTeam(r.groupId));

        return $q.all(promises);
    },
    projects: (user, projectService) => {
        return projectService.query(
            {
                sort: 'createdAt,desc',
                pageSize: 10,
                page: 1,
                ownershipType: 'owned'
            }
        );
    },
    rasters: (user, sceneService) => {
        return sceneService.query(
            {
                sort: 'createdAt,desc',
                pageSize: 10,
                page: 1,
                ownershipType: 'owned'
            }
        );
    },
    vectors: (user, shapesService) => {
        return shapesService.query(
            {
                sort: 'createdAt,desc',
                pageSize: 10,
                page: 1,
                ownershipType: 'owned'
            }
        );
    },
    datasources: (user, datasourceService) => {
        return datasourceService.query(
            {
                sort: 'createdAt,desc',
                pageSize: 10,
                page: 1,
                ownershipType: 'owned'
            }
        );
    },
    templates: (user, analysisService) => {
        return analysisService.fetchTemplates(
            {
                sort: 'createdAt,desc',
                pageSize: 10,
                page: 1,
                ownershipType: 'owned'
            }
        );
    },
    analyses: (user, analysisService) => {
        return analysisService.fetchAnalyses(
            {
                sort: 'createdAt,desc',
                pageSize: 10,
                page: 1,
                ownershipType: 'owned'
            }
        );
    }
};

UserModule.controller('UserController', UserController);

export default UserModule;

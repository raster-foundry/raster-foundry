import _ from 'lodash';
import ProjectsController from './projects.controller.js';
import autoInject from '_appRoot/autoInject';
const ProjectsModule = angular.module('pages.projects', []);

ProjectsModule.resolve = {
    user: ($stateParams, authService) => {
        return authService.getCurrentUser();
    },
    userRoles: (authService) => {
        return authService.fetchUserRoles();
    },
    platform: (userRoles, platformService) => {
        const platformRole = userRoles.find(r =>
            r.groupType === 'PLATFORM'
        );

        return platformService.getPlatform(platformRole.groupId);
    },
    organizationRoles: (userRoles) => {
        return _.uniqBy(userRoles, r => r.groupId).filter(r => r.groupType === 'ORGANIZATION');
    },
    organizations: ($q, organizationRoles, organizationService) => {
        return $q.all(organizationRoles.map(r => organizationService.getOrganization(r.groupId)));
    },
    teamRoles: (userRoles) => {
        return _.uniqBy(userRoles, r => r.groupId).filter(r => r.groupType === 'TEAM');
    },
    teams: ($q, teamRoles, teamService) => {
        return $q.all(teamRoles.map(r => teamService.getTeam(r.groupId)));
    }
};

autoInject(ProjectsModule);

ProjectsModule.controller('ProjectsController', ProjectsController);

export default ProjectsModule;

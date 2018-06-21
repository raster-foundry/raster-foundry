import angular from 'angular';

class OrganizationSettingsController {
    constructor(
        $stateParams, authService,
        platform, organization, user, userRoles
    ) {
        'ngInject';

        this.$stateParams = $stateParams;
        this.authService = authService;
        this.platform = platform;
        this.organization = organization;
        this.user = user;
        this.userRoles = userRoles;
    }
}

const OrganizationSettingsModule = angular.module('pages.organization.settings', []);
OrganizationSettingsModule.controller(
    'OrganizationSettingsController', OrganizationSettingsController
);

export default OrganizationSettingsModule;

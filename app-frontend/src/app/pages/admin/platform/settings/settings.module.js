import angular from 'angular';

class PlatformSettingsController {
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

const PlatformSettingsModule = angular.module('pages.platform.settings', []);

PlatformSettingsModule.controller(
    'PlatformSettingsController',
    PlatformSettingsController
);

export default PlatformSettingsModule;

import angular from 'angular';

class PlatformSettingsController {
    constructor(
        $stateParams, authService,
        platform
    ) {
        'ngInject';

        this.$stateParams = $stateParams;
        this.authService = authService;
        this.platform = platform;
    }

    $onInit() {
        this.authService.isEffectiveAdmin(this.platform.id);
    }
}

const PlatformSettingsModule = angular.module('pages.platform.settings', []);

PlatformSettingsModule.controller(
    'PlatformSettingsController',
    PlatformSettingsController
);

export default PlatformSettingsModule;

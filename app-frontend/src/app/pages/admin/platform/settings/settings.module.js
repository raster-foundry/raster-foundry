import angular from 'angular';

class PlatformSettingsController {
    constructor($stateParams, authService) {
        'ngInject';

        this.$stateParams = $stateParams;
        this.authService = authService;
    }

    $onInit() {
    }
}

const PlatformSettingsModule = angular.module('pages.platform.settings', []);

PlatformSettingsModule.controller(
    'PlatformSettingsController',
    PlatformSettingsController
);

export default PlatformSettingsModule;

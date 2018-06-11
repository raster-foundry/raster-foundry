import angular from 'angular';

class PlatformSettingsController {
    constructor() {
        'ngInject';
    }
}

const PlatformSettingsModule = angular.module('pages.platform.settings', []);

PlatformSettingsModule.controller(
    'PlatformSettingsController',
    PlatformSettingsController
);

export default PlatformSettingsModule;

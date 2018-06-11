import angular from 'angular';

class PlatformEmailController {
    constructor() {
        'ngInject';
    }
}

const PlatformEmailModule = angular.module('pages.platform.settings.email', []);

PlatformEmailModule.controller(
    'PlatformEmailController',
    PlatformEmailController
);

export default PlatformEmailModule;

import angular from 'angular';

class PlatformSettingsController {
    constructor($scope) {
        'ngInject';

        this.$scope = $scope;
    }

    $onInit() {
        this.isSuperOrAdmin = this.$scope.$parent.$ctrl.isSuperOrAdmin;
    }
}

const PlatformSettingsModule = angular.module('pages.platform.settings', []);

PlatformSettingsModule.controller(
    'PlatformSettingsController',
    PlatformSettingsController
);

export default PlatformSettingsModule;

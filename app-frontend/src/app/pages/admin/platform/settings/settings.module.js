import angular from 'angular';

class PlatformSettingsController {
    constructor($scope) {
        'ngInject';

        this.$scope = $scope;
    }

    $onInit() {
        this.$scope.$parent.$ctrl.isSuperOrAdminPromise.then(res => {
            this.isSuperOrAdmin = res;
        });
    }
}

const PlatformSettingsModule = angular.module('pages.platform.settings', []);

PlatformSettingsModule.controller(
    'PlatformSettingsController',
    PlatformSettingsController
);

export default PlatformSettingsModule;

import angular from 'angular';

class PlatformController {
    constructor($stateParams, platformService) {
        'ngInject';
        this.$stateParams = $stateParams;
        this.platformService = platformService;
        this.fetching = true;
    }

    $onInit() {
        this.platformService
            .getPlatform(this.$stateParams.platformId)
            .then((platform) => {
                this.platform = platform;
                this.fetching = false;
            });
    }
}

const PlatformModule = angular.module('pages.platform', []);
PlatformModule.controller('PlatformController', PlatformController);

export default PlatformModule;

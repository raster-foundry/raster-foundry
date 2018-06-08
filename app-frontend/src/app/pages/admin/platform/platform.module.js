import angular from 'angular';

class PlatformController {
    constructor($stateParams, platformService, authService) {
        'ngInject';
        this.$stateParams = $stateParams;
        this.platformService = platformService;
        this.authService = authService;
        this.fetching = true;
    }

    $onInit() {
        this.platformService
            .getPlatform(this.$stateParams.platformId)
            .then((platform) => {
                this.platform = platform;
                this.fetching = false;
            });
        this.currentUserPromise = this.authService.getCurrentUser().then();
        this.currentUgrPromise = this.authService.fetchUserRoles().then();
    }
}

const PlatformModule = angular.module('pages.platform', []);
PlatformModule.controller('PlatformController', PlatformController);

export default PlatformModule;

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

        this.currentUserPromise.then(user => {
            this.currentUgrPromise.then(ugrs => {
                let platUgr = ugrs.filter(ugr => ugr.groupId === this.$stateParams.platformId)[0];
                if (platUgr) {
                    this.isSuperOrAdmin = user.isActive && user.isSuperuser ||
                        platUgr.isActive && platUgr.groupRole === 'ADMIN';
                }
            });
        });
    }
}

const PlatformModule = angular.module('pages.platform', []);
PlatformModule.controller('PlatformController', PlatformController);

export default PlatformModule;

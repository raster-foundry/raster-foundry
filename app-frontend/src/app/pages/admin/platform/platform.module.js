/* global BUILDCONFIG */

import angular from 'angular';
let assetLogo = BUILDCONFIG.LOGOFILE ?
    require(`../../../../assets/images/${BUILDCONFIG.LOGOFILE}`) :
    require('../../../../assets/images/raster-foundry-logo.svg');

assetLogo = BUILDCONFIG.LOGOURL || assetLogo;

class PlatformController {
    constructor(
        $stateParams,
        platformService, authService,
        platform, members, organizations
    ) {
        'ngInject';
        this.$stateParams = $stateParams;
        this.platformService = platformService;
        this.authService = authService;
        this.platformLogo = assetLogo;
        this.platform = platform;
        this.organizations = organizations;
        this.users = members;
    }

    $onInit() {
        this.isEffectiveAdmin = this.authService.isEffectiveAdmin(this.platform.id);
    }
}

const PlatformModule = angular.module('pages.platform', []);

PlatformModule.resolve = {
    platform: ($stateParams, platformService) => {
        return platformService.getPlatform($stateParams.platformId);
    },
    user: (authService) => {
        return authService.getCurrentUser();
    },
    members: (platform, platformService) => {
        return platformService.getMembers(platform.id, 0, '');
    },
    organizations: (platform, platformService) => {
        return platformService.getOrganizations(platform.id, 0, '');
    }
};

PlatformModule.controller('PlatformController', PlatformController);

export default PlatformModule;

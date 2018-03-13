import angular from 'angular';

class PlatformOrganizationDetailController {
    constructor() {

    }
}

const PlatformOrganizationDetailModule = angular.module('pages.platform.organizations.detail', []);
PlatformOrganizationDetailModule.controller(
    'PlatformOrganizationDetailController', PlatformOrganizationDetailController
);

export default PlatformOrganizationDetailModule;

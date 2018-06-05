import angular from 'angular';

class OrganizationController {
    constructor(
      $stateParams,
      organizationService, authService) {
        'ngInject';
        this.$stateParams = $stateParams;

        this.organizationService = organizationService;
        this.authService = authService;
        this.fetching = true;
    }

    $onInit() {
        this.organizationService
            .getOrganization(this.$stateParams.organizationId)
            .then((organization) => {
                this.organization = organization;
                this.fetching = false;
            });
        this.currentUserPromise = this.authService.getCurrentUser().then();
        this.currentUgrPromise = this.authService.fetchUserRoles().then();
    }
}

const OrganizationModule = angular.module('pages.organization', []);
OrganizationModule.controller('OrganizationController', OrganizationController);

export default OrganizationModule;

import angular from 'angular';

class OrganizationController {
    constructor($stateParams, organizationService) {
        'ngInject';
        this.$stateParams = $stateParams;
        this.organizationService = organizationService;
        this.fetching = true;
    }

    $onInit() {
        this.organizationService
            .getOrganization(this.$stateParams.organizationId)
            .then((organization) => {
                this.organization = organization;
                this.fetching = false;
            });
    }
}

const OrganizationModule = angular.module('pages.organization', []);
OrganizationModule.controller('OrganizationController', OrganizationController);

export default OrganizationModule;

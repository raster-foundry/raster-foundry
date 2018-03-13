import angular from 'angular';

class OrganizationMetricsController {
    constructor() {
        //eslint-disable-next-line
        console.log('Organization Metrics Controller');
    }
}

const OrganizationMetricsModule = angular.module('pages.organization.metrics', []);
OrganizationMetricsModule.controller(
    'OrganizationMetricsController', OrganizationMetricsController
);

export default OrganizationMetricsModule;

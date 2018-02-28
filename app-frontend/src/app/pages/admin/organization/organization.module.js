import angular from 'angular';

class OrganizationController {
    constructor() {
        //eslint-disable-next-line
        console.log('Organization Controller');
    }
}

const OrganizationModule = angular.module('pages.organization', []);
OrganizationModule.controller('OrganizationController', OrganizationController);

export default OrganizationModule;

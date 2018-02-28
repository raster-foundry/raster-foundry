import angular from 'angular';

class OrganizationSettingsController {
    constructor() {
        //eslint-disable-next-line
        console.log('Organization Settings Controller');
    }
}

const OrganizationSettingsModule = angular.module('pages.organization.settings', []);
OrganizationSettingsModule.controller(
    'OrganizationSettingsController', OrganizationSettingsController
);

export default OrganizationSettingsModule;

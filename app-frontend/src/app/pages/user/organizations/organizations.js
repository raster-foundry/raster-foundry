class UserOrganizationsController {
    constructor(organizations) {
        this.organizations = organizations;
    }
}

const Module = angular.module('pages.user.organizations', []);

Module.controller('UserOrganizationsController', UserOrganizationsController);

export default Module;

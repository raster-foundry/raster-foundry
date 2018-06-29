class UserOrganizationsController {
    constructor(organizationRoles, organizations) {
        this.organizationRoles = organizationRoles;
        this.organizations = organizations;
    }

    getUserOrgRole(orgId) {
        return this.organizationRoles.find(role => role.groupId === orgId).groupRole;
    }
}

const Module = angular.module('pages.user.organizations', []);

Module.controller('UserOrganizationsController', UserOrganizationsController);

export default Module;

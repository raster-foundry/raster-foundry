class UserOrganizationsController {
    constructor(organizations) {
        this.userRoles = organizations.roles;
        this.organizations = [];
        organizations.orgs.forEach(orgPromise => {
            orgPromise.then(org => {
                this.organizations.push(org);
            });
        });
    }

    getUserOrgRole(orgId) {
        return this.userRoles.find(role => role.groupId === orgId).groupRole;
    }
}

const Module = angular.module('pages.user.organizations', []);

Module.controller('UserOrganizationsController', UserOrganizationsController);

export default Module;

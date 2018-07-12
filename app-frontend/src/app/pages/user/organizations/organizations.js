class UserOrganizationsController {
    constructor(
        $scope, $state,
        organizationService,
        organizationRoles, organizations, user
    ) {
        'ngInject';
        $scope.autoInject(this, arguments);
    }

    getUserOrgRole(orgId) {
        return this.organizationRoles.find(role => role.groupId === orgId).groupRole;
    }

    membershipPending(organization) {
        return !!this.organizationRoles.find(r =>
                r.groupType === 'ORGANIZATION' &&
                r.groupId === organization.id &&
                r.membershipStatus === 'INVITED'
            );
    }

    updateUserMembershipStatus(organization, isApproved) {
        if (isApproved) {
            const role = this.organizationRoles.find(r =>
                r.groupType === 'ORGANIZATION' &&
                r.groupId === organization.id
            ).groupRole;
            if (role) {
                this.organizationService.approveUserMembership(
                    organization.platformId,
                    organization.id,
                    this.user.id,
                    role
                ).then(resp => {
                    this.$state.reload();
                });
            }
        } else {
            this.organizationService.removeUser(
                organization.platformId,
                organization.id,
                this.user.id
            ).then(resp => {
                this.$state.reload();
            });
        }
    }
}

const Module = angular.module('pages.user.organizations', []);

Module.controller('UserOrganizationsController', UserOrganizationsController);

export default Module;

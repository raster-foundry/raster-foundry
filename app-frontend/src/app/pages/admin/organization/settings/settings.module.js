import angular from 'angular';

class OrganizationSettingsController {
    constructor(
        $stateParams, authService, organizationService,
        platform, organization, user, userRoles
    ) {
        'ngInject';

        this.$stateParams = $stateParams;
        this.authService = authService;
        this.organizationService = organizationService;
        this.platform = platform;
        this.organization = organization;
        this.user = user;
        this.userRoles = userRoles;
    }

    updateOrgPrivacy(privacy) {
        const previousValue = this.organization.visibility;
        this.organization.visibility = privacy;
        this.organizationService.updateOrganization(
            this.platform.id, this.organization.id, this.organization
        ).catch(() => {
            this.organization.visibility = previousValue;
        });
    }
}

const OrganizationSettingsModule = angular.module('pages.organization.settings', []);
OrganizationSettingsModule.controller(
    'OrganizationSettingsController', OrganizationSettingsController
);

export default OrganizationSettingsModule;

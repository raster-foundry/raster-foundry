import angular from 'angular';

class OrganizationController {
    constructor(
      $stateParams,
      organizationService, authService, modalService) {
        'ngInject';
        this.$stateParams = $stateParams;

        this.organizationService = organizationService;
        this.authService = authService;
        this.modalService = modalService;
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

    onLogoHovered(isHovered) {
        this.onLogoHover = isHovered;
    }

    addLogoModal() {
        this.modalService.open({
            component: 'rfAddPhotoModal',
            resolve: {
                organizationId: () => this.organization.id
            }
        }).result.then(() => {
            // TODO
        });
    }
}

const OrganizationModule = angular.module('pages.organization', []);
OrganizationModule.controller('OrganizationController', OrganizationController);

export default OrganizationModule;

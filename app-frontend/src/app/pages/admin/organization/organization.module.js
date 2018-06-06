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
                this.orgLogoUri = this.setOrgLogoUri(this.organization.logoUri);
                this.fetching = false;
            });
        this.currentUserPromise = this.authService.getCurrentUser().then();
        this.currentUgrPromise = this.authService.fetchUserRoles().then();
        this.isLogoChangeAllowed();
    }

    isLogoChangeAllowed() {
        // need to be a superuser or an org admin to change logo
        let self = this;
        this.currentUserPromise.then(respUser => {
            self.currentUgrPromise.then((respUgr) => {
                let currentOrgUgr = respUgr.filter((ugr) => {
                    return ugr.groupId === self.$stateParams.organizationId;
                })[0];
                self.isSuperOrAdmin = respUser.isSuperuser ||
                    currentOrgUgr && currentOrgUgr.groupRole === 'ADMIN';
            });
        });
    }

    onLogoHovered(isHovered) {
        this.onLogoHover = isHovered;
    }

    addLogoModal() {
        this.modalService.open({
            component: 'rfAddPhotoModal',
            resolve: {
                organizationId: () => this.$stateParams.organizationId
            }
        }).result.then((resp) => {
            this.onLogoHover = false;
            this.organization = Object.assign({}, this.organization, resp);
            this.orgLogoUri = this.setOrgLogoUri(this.organization.logoUri);
        }, () => {
            this.onLogoHover = false;
        });
    }

    setOrgLogoUri(uri) {
    // to differentiate logos since uri itself does not change
    // but the picture itself changes
        return uri.length ? `${uri}?${new Date().getTime()}` : '';
    }
}

const OrganizationModule = angular.module('pages.organization', []);
OrganizationModule.controller('OrganizationController', OrganizationController);

export default OrganizationModule;

import angular from 'angular';

class OrganizationController {
    constructor(
      $stateParams, $q,
      organizationService, authService, modalService) {
        'ngInject';

        this.$stateParams = $stateParams;
        this.$q = $q;
        this.organizationService = organizationService;
        this.authService = authService;
        this.modalService = modalService;
        this.fetching = true;
    }

    $onInit() {
        this.currentOrgPromise = this.organizationService
            .getOrganization(this.$stateParams.organizationId)
            .then((organization) => {
                this.organization = organization;
                this.orgLogoUri = this.setOrgLogoUri(this.organization.logoUri);
                this.fetching = false;
                return organization;
            });
        this.currentUserPromise = this.authService.getCurrentUser();
        this.currentUgrPromise = this.authService.fetchUserRoles();
        this.isLogoChangeAllowed();
    }

    isLogoChangeAllowed() {
        // need to be a superuser or a platform/org admin to change logo
        this.$q.all({
            respOrg: this.currentOrgPromise,
            respUser: this.currentUserPromise,
            respUgr: this.currentUgrPromise
        }).then(({respOrg, respUser, respUgr}) => {
            let currentOrgUgr = respUgr.find(ugr => {
                return ugr.groupId === respOrg.id;
            });
            let currentPlatUgr = respUgr.find(ugr => {
                return ugr.groupId === respOrg.platformId;
            });
            this.isSuperOrAdmin =
                respUser.isSuperuser ||
                currentOrgUgr && currentOrgUgr.groupRole === 'ADMIN' ||
                currentPlatUgr && currentPlatUgr.groupRole === 'ADMIN';
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

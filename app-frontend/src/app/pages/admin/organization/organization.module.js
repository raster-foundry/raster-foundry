import angular from 'angular';

class OrganizationController {
    constructor(
      $stateParams, $q, $log,
      organizationService, authService, modalService) {
        'ngInject';

        this.$stateParams = $stateParams;
        this.$q = $q;
        this.$log = $log;
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
            let adminOrgUgr = respUgr.find(ugr => {
                return ugr.groupId === respOrg.id && ugr.groupRole === 'ADMIN';
            });
            let adminPlatUgr = respUgr.find(ugr => {
                return ugr.groupId === respOrg.platformId && ugr.groupRole === 'ADMIN';
            });
            this.isSuperOrAdmin = respUser.isSuperuser || adminOrgUgr || adminPlatUgr;
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

    toggleOrgNameEdit() {
        this.isEditOrgName = !this.isEditOrgName;
        delete this.nameBuffer;
    }

    finishOrgNameEdit() {
        if (this.nameBuffer && this.nameBuffer.length
            && this.nameBuffer !== this.organization.name) {
            let orgUpdated = Object.assign({}, this.organization, {name: this.nameBuffer});
            this.organizationService.updateOrganization(
              this.organization.platformId, this.organization.id, orgUpdated)
            .then(resp => {
                this.organization = resp;
            });
        }
        delete this.nameBuffer;
        delete this.isEditOrgName;
    }
}

const OrganizationModule = angular.module('pages.organization', []);
OrganizationModule.controller('OrganizationController', OrganizationController);

export default OrganizationModule;

class OrganizationSettingsController {
    constructor(modalService) {
        this.modalService = modalService;
    }

    userModal() {
        this.modalService.open({
            component: 'rfUserModal',
            resolve: { },
            size: 'sm'
        }).result.then((result) => {
            // eslint-disable-next-line
            console.log('user modal closed with value:', result);
        });
    }

    teamModal() {
        this.modalService.open({
            component: 'rfTeamModal',
            resolve: { },
            size: 'sm'
        }).result.then((result) => {
            // eslint-disable-next-line
            console.log('team modal closed with value:', result);
        });
    }

    organizationModal() {
        this.modalService.open({
            component: 'rfOrganizationModal',
            resolve: { },
            size: 'sm'
        }).result.then((result) => {
            // eslint-disable-next-line
            console.log('organization modal closed with value:', result);
        });
    }
}

const OrganizationSettingsModule = angular.module('pages.settings.organizations', []);

OrganizationSettingsModule
    .controller('OrganizationSettingsController', OrganizationSettingsController);

export default OrganizationSettingsModule;

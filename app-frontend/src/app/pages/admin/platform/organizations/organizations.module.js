import angular from 'angular';

class PlatformOrganizationsController {
    constructor($state, modalService) {
        this.$state = $state;
        this.modalService = modalService;
        this.fetchUsers();
    }

    fetchUsers() {
        this.organizations = [
            {
                id: '1',
                name: 'organization one',
                email: 'admin@organization.com',
                subscription: {
                    name: '30 day trial'
                },
                userCount: 1
            },
            {
                id: '2',
                name: 'organization two',
                email: 'admin@organization.com',
                subscription: {
                    name: 'Pro Package'
                },
                userCount: 10
            },
            {
                id: '3',
                name: 'organization three',
                email: 'admin@organization.com',
                subscription: {
                    name: 'Pro Package'
                },
                userCount: 12
            },
            {
                id: '4',
                name: 'organization four',
                email: 'admin@organization.com',
                subscription: {
                    name: 'Super Pro Plus Delux Package'
                },
                userCount: 500
            }
        ];

        this.organizations.forEach(
            (org) => Object.assign(
                org, {
                    options: {
                        items: this.itemsForOrg(org)
                    }
                }
            ));
    }

    itemsForOrg(organization) {
        /* eslint-disable */
        return [
            {
                label: 'Manage',
                callback: () => {
                    this.$state.go('.detail.features', {orgId: organization.id});
                }
            },
            {
                label: 'Delete',
                callback: () => {
                    console.log('delete callback for organization:', organization);
                },
                classes: ['color-danger']
            }
        ];
        /* eslint-enable */
    }

    newOrgModal() {
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

const PlatformOrganizationsModule = angular.module('pages.platform.organizations', []);
PlatformOrganizationsModule.controller(
    'PlatformOrganizationsController', PlatformOrganizationsController
);

export default PlatformOrganizationsModule;

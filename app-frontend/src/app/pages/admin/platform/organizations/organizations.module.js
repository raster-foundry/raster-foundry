import angular from 'angular';
import _ from 'lodash';

class PlatformOrganizationsController {
    constructor(
        $state, modalService, $stateParams, $scope, $log, $window,
        platformService, organizationService, authService,
        platform
    ) {
        this.$state = $state;
        this.$scope = $scope;
        this.$stateParams = $stateParams;
        this.$log = $log;
        this.$window = $window;
        this.modalService = modalService;
        this.platformService = platformService;
        this.organizationService = organizationService;
        this.authService = authService;
        this.platform = platform;
    }

    $onInit() {
        this.isEffectiveAdmin = this.authService.isEffectiveAdmin(this.platform.id);

        this.debouncedSearch = _.debounce(
            this.onSearch.bind(this),
            500,
            {leading: false, trailing: true}
        );

        this.fetchOrganizations(1, '');
    }

    onSearch(search) {
        this.fetchOrganizations(1, search);
    }

    updatePagination(data) {
        this.pagination = {
            show: data.count > data.pageSize,
            count: data.count,
            currentPage: data.page + 1,
            startingItem: data.page * data.pageSize + 1,
            endingItem: Math.min((data.page + 1) * data.pageSize, data.count),
            hasNext: data.hasNext,
            hasPrevious: data.hasPrevious
        };
    }

    fetchOrganizations(page = 1, search) {
        this.fetching = true;
        this.platformService
            .getOrganizations(this.$stateParams.platformId, page - 1, search)
            .then((response) => {
                this.fetching = false;
                this.updatePagination(response);
                this.lastOrgResult = response;
                this.organizations = response.results;

                this.organizations.forEach(org => {
                    Object.assign(org, {
                        options: {
                            items: this.itemsForOrg(org)
                        },
                        showOptions: this.isEffectiveAdmin
                    });
                    this.organizationService
                        .getMembers(this.$stateParams.platformId, org.id)
                        .then((paginatedUsers) => {
                            org.fetchedUsers = paginatedUsers;
                        });
                });
            });
    }


    itemsForOrg(organization) {
        /* eslint-disable */
        let actions = [];
        if (organization.isActive) {
            actions.push({
                label: 'Deactivate',
                callback: () => {
                    this.deactivateOrganization(organization);
                },
                classes: ['color-danger']
            });
        } else {
            actions.push({
                label: 'Activate',
                callback: () => {
                    this.activateOrganization(organization);
                }
            });
        };
        return actions;
        /* eslint-enable */
    }

    deactivateOrganization(organization) {
        const modal = this.modalService.open({
            component: 'rfConfirmationModal',
            size: 'sm',
            resolve: {
                title: () => 'Deactivate organization ?',
                content: () => 'The organization can be reactivated at any time',
                confirmText: () => 'Deactivate',
                cancelText: () => 'Cancel'
            }
        });

        modal.result.then(() => {
            this.organizationService.deactivate(
                this.$stateParams.platformId, organization.id
            ).then(
                () => {
                    this.fetchOrganizations(this.pagination.currentPage);
                },
                (err) => {
                    this.$log.debug('error deactivating organization', err);
                    this.fetchOrganizations(this.pagination.currentPage);
                }
            );
        });
    }

    activateOrganization(organization) {
        this.organizationService.activate(
            this.$stateParams.platformId, organization.id
        ).then(
            () => {
                this.fetchOrganizations(this.pagination.currentPage);
            },
            (err) => {
                this.$log.debug('error activating organization', err);
                this.fetchOrganizations(this.pagination.currentPage);
            }
        );
    }

    newOrgModal() {
        this.modalService.open({
            component: 'rfOrganizationModal',
            size: 'sm'
        }).result.then((result) => {
            this.platformService
                .createOrganization(this.$stateParams.platformId, result.name, 'ACTIVE')
                .then(() => {
                    this.fetchOrganizations();
                });
        });
    }

    toggleOrgNameEdit(orgId, isEdit) {
        this.nameBuffer = '';
        this.editOrgId = isEdit ? orgId : null;
        this.isEditOrgName = isEdit;
    }

    finishOrgNameEdit(org) {
        if (this.nameBuffer && this.nameBuffer.length && org.name !== this.nameBuffer) {
            let orgUpdated = Object.assign({}, org, {name: this.nameBuffer});
            this.organizationService
                .updateOrganization(orgUpdated.platformId, orgUpdated.id, orgUpdated)
                .then(resp => {
                    this.organizations[this.organizations.indexOf(org)] = resp;
                }, () => {
                    this.$window.alert('Organization\'s name cannot be updated at the moment.');
                }).finally(() => {
                    delete this.editOrgId;
                    delete this.isEditOrgName;
                    this.nameBuffer = '';
                });
        } else {
            delete this.editOrgId;
            delete this.isEditOrgName;
            this.nameBuffer = '';
        }
    }

    getInitialNameBuffer(orgId) {
        let organization = this.organizations.find(org => org.id === orgId);
        return organization ? organization.name : '';
    }
}

const PlatformOrganizationsModule = angular.module('pages.platform.organizations', []);
PlatformOrganizationsModule.controller(
    'PlatformOrganizationsController', PlatformOrganizationsController
);

export default PlatformOrganizationsModule;

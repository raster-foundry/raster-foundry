import angular from 'angular';
import _ from 'lodash';

class PlatformOrganizationsController {
    constructor(
        $state, modalService, $stateParams, $scope, $log, $window,
        platformService, organizationService, authService, paginationService,
        platform
    ) {
        'ngInject';
        $scope.autoInject(this, arguments);
    }

    $onInit() {
        this.isEffectiveAdmin = this.authService.isEffectiveAdmin(this.platform.id);
        this.fetchPage();
    }

    fetchPage(page = this.$state.params.page || 1, search = this.$state.params.search) {
        this.search = search && search.length ? search : null;
        delete this.fetchError;
        this.results = [];
        const currentQuery = this.platformService
            .getOrganizations(
                this.$stateParams.platformId,
                page - 1,
                this.search
            ).then(paginatedResponse => {
                this.results = paginatedResponse.results;
                this.pagination = this.paginationService.buildPagination(paginatedResponse);
                this.paginationService.updatePageParam(page, this.search);
                this.buildOptions();
            }, (e) => {
                if (this.currentQuery === currentQuery) {
                    this.fetchError = e;
                }
            }).finally(() => {
                if (this.currentQuery === currentQuery) {
                    delete this.currentQuery;
                }
            });
        this.currentQuery = currentQuery;
    }

    buildOptions() {
        this.results.forEach(org => {
            Object.assign(org, {
                options: {
                    items: this.itemsForOrg(org)
                },
                showOptions: this.isEffectiveAdmin
            });
            this.organizationService
                .getMembers(this.$stateParams.platformId, org.id)
                .then(paginatedUsers => {
                    org.fetchedUsers = paginatedUsers;
                });
        });
    }


    itemsForOrg(organization) {
        /* eslint-disable */
        let actions = [];
        if (organization.status === 'ACTIVE') {
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
                    this.fetchPage(this.pagination.currentPage);
                },
                (err) => {
                    this.$log.debug('error deactivating organization', err);
                    this.fetchPage(this.pagination.currentPage);
                }
            );
        });
    }

    activateOrganization(organization) {
        this.organizationService.activate(
            this.$stateParams.platformId, organization.id
        ).then(
            () => {
                this.fetchPage(this.pagination.currentPage);
            },
            (err) => {
                this.$log.debug('error activating organization', err);
                this.fetchPage(this.pagination.currentPage);
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
                    this.fetchPage();
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

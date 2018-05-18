import angular from 'angular';
import _ from 'lodash';

class PlatformOrganizationsController {
    constructor(
        $state, modalService, $stateParams, $scope, $log,
        platformService, organizationService
    ) {
        this.$state = $state;
        this.$scope = $scope;
        this.$stateParams = $stateParams;
        this.$log = $log;
        this.modalService = modalService;
        this.platformService = platformService;
        this.organizationService = organizationService;
        if (!this.$stateParams.platformId) {
            this.$state.go('admin');
        }
        this.fetching = true;

        let debouncedSearch = _.debounce(
            this.onSearch.bind(this),
            500,
            {leading: false, trailing: true}
        );
        this.$scope.$watch('$ctrl.search', debouncedSearch);
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

                this.organizations.forEach(
                    (org) => Object.assign(
                        org, {
                            options: {
                                items: this.itemsForOrg(org)
                            }
                        }
                    ));

                this.organizations.forEach(
                    (organization) => {
                        this.organizationService
                            .getMembers(this.$stateParams.platformId, organization.id)
                            .then((paginatedUsers) => {
                                organization.fetchedUsers = paginatedUsers;
                            });
                    }
                );
            });
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
                label: 'Deactivate',
                callback: () => {
                    this.deactivateOrganization(organization);
                },
                classes: ['color-danger']
            }
        ];
        /* eslint-enable */
    }

    deactivateOrganization(organization) {
        const modal = this.modalService.open({
            component: 'rfConfirmationModal',
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

    newOrgModal() {
        this.modalService.open({
            component: 'rfOrganizationModal',
            resolve: { },
            size: 'sm'
        }).result.then((result) => {
            this.platformService
                .createOrganization(this.$stateParams.platformId, result.name)
                .then(() => {
                    this.fetchOrganizations();
                });
        });
    }
}

const PlatformOrganizationsModule = angular.module('pages.platform.organizations', []);
PlatformOrganizationsModule.controller(
    'PlatformOrganizationsController', PlatformOrganizationsController
);

export default PlatformOrganizationsModule;

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

        this.$scope.$parent.$ctrl.currentUserPromise.then(resp => {
            this.currentUser = resp;
        });
        this.currentUgrPromise = this.$scope.$parent.$ctrl.currentUgrPromise;
        this.getPlatUgrs();
        this.$scope.$watch('$ctrl.search', debouncedSearch);
    }

    getPlatUgrs() {
        this.currentUgrPromise.then((resp) => {
            this.currentPlatUgr = resp.filter((ugr) => {
                return ugr.groupId === this.$stateParams.platformId;
            })[0];
            this.isPlatAdmin = this.currentPlatUgr && this.currentPlatUgr.groupRole === 'ADMIN';
        });
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

                this.organizations.forEach((org) => {
                    this.currentUgrPromise.then((resp) => {
                        // eslint-disable-next-line
                        this.currentOrgUgr = resp.filter((ugr) => {
                            return ugr.groupId === org.id;
                        })[0];
                        this.isPlatOrOrgAdmin =
                            this.currentOrgUgr && this.currentOrgUgr.groupRole === 'ADMIN' ||
                            this.isPlatAdmin;
                        Object.assign(org, {
                            options: {
                                items: this.itemsForOrg(org)
                            },
                            showOptions: this.currentUser.isSuperuser || this.isPlatOrOrgAdmin
                        });
                    });
                });

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
        let actions = [{
            label: 'Manage',
            callback: () => {
                this.$state.go('.detail.features', {orgId: organization.id});
            }
        }];
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
                },
                classes: ['color-secondary']
            });
        };
        return actions;
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
        let permissionDenied = {};
        if (!(this.currentUser.isActive &&
            (this.currentUser.isSuperuser || this.isPlatAdmin))) {
            permissionDenied = {
                isDenied: true,
                adminEmail: 'example@email.com',
                message: 'You do not have access to this operation. Please contact ',
                subject: 'platform admin'
            };
        }
        this.modalService.open({
            component: 'rfOrganizationModal',
            resolve: {
                permissionDenied: permissionDenied
            },
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

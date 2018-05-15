import angular from 'angular';
import _ from 'lodash';

class OrganizationUsersController {
    constructor(
        $scope, $stateParams,
        modalService, organizationService
    ) {
        this.$scope = $scope;
        this.$stateParams = $stateParams;
        this.modalService = modalService;
        this.organizationService = organizationService;
        this.fetching = true;

        let debouncedSearch = _.debounce(
            this.onSearch.bind(this),
            500,
            {leading: false, trailing: true}
        );

        this.orgWatch = this.$scope.$parent.$watch('$ctrl.organization', (organization) => {
            if (organization && this.orgWatch) {
                this.orgWatch();
                delete this.orgWatch;
                this.organization = organization;
                this.$scope.$watch('$ctrl.search', debouncedSearch);
            }
        });
    }

    onSearch(search) {
        this.fetchUsers(1, search);
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

    fetchUsers(page = 1, search) {
        const platformId = this.organization.platformId;
        const organizationId = this.organization.id;
        this.fetching = true;
        this.organizationService
            .getMembers(platformId, organizationId, page - 1, search)
            .then((response) => {
                this.fetching = false;
                this.updatePagination(response);
                this.lastUserResult = response;
                this.users = response.results;

                this.users.forEach(
                    (user) => Object.assign(
                        user, {
                            options: {
                                items: this.itemsForUser(user)
                            }
                        }
                    ));
            });
    }

    itemsForUser(user) {
        /* eslint-disable */
        return [
            {
                label: 'Edit',
                callback: () => {
                    console.log('edit callback for user:', user);
                }
            }
            // {
            //     label: 'Delete',
            //     callback: () => {
            //         console.log('delete callback for user:', user);
            //     },
            //     classes: ['color-danger']
            // }
        ];
        /* eslint-enable */
    }

    newUserModal() {
        this.modalService.open({
            component: 'rfUserModal',
            resolve: { },
            size: 'sm'
        }).result.then((result) => {
            // eslint-disable-next-line
            console.log('user modal closed with value:', result);
        });
    }
}

const OrganizationUsersModule = angular.module('pages.organization.users', []);
OrganizationUsersModule.controller('OrganizationUsersController', OrganizationUsersController);

export default OrganizationUsersModule;

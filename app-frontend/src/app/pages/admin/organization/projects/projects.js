import angular from 'angular';
import _ from 'lodash';

class OrganizationProjectsController {
    constructor(
        $scope, $stateParams, $log, $window,
        modalService, authService, projectService, paginationService,
        platform, organization, members, teams
    ) {
        'ngInject';
        $scope.autoInject(this, arguments);
    }

    $onInit() {
        this.searchTerm = '';
        this.loading = false;
        this.onSearch = this.paginationService.buildPagedSearch(this);

        this.isEffectiveAdmin = this.authService.isEffectiveAdmin([
            this.platform.id,
            this.organization.id
        ]);

        this.fetchPage();
    }

    fetchPage(page = this.$stateParams.page || 1) {
        this.loading = true;
        this.projectService.query(
            {
                sort: 'createdAt,desc',
                pageSize: 10,
                ownershipType: 'inherited',
                groupType: 'organization',
                groupId: this.organization.id,
                search: this.searchTerm,
                page: page - 1
            }
        ).then(paginatedResponse => {
            this.results = paginatedResponse.results;
            this.pagination = this.paginationService.buildPagination(paginatedResponse);
            this.paginationService.updatePageParam(page);
        }).finally(() => {
            this.loading = false;
        });
    }
}

export default angular
    .module('pages.organization.projects', [])
    .controller('OrganizationProjectsController', OrganizationProjectsController);

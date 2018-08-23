import angular from 'angular';
import _ from 'lodash';

class OrganizationProjectsController {
    constructor(
        $scope, $state, $log, $window,
        modalService, authService, projectService, paginationService,
        platform, organization, members, teams
    ) {
        'ngInject';
        $scope.autoInject(this, arguments);
    }

    $onInit() {
        this.isEffectiveAdmin = this.authService.isEffectiveAdmin([
            this.platform.id,
            this.organization.id
        ]);

        this.fetchPage();
    }

    fetchPage(page = this.$state.page || 1, search = this.$state.params.search) {
        this.search = search && search.length ? search : null;
        delete this.fetchError;
        this.results = [];
        const currentQuery = this.projectService.query({
            sort: 'createdAt,desc',
            pageSize: 10,
            ownershipType: 'inherited',
            groupType: 'organization',
            groupId: this.organization.id,
            search: this.search,
            page: page - 1
        }).then(paginatedResponse => {
            this.results = paginatedResponse.results;
            this.pagination = this.paginationService.buildPagination(paginatedResponse);
            this.paginationService.updatePageParam(page, this.search);
            if (this.currentQuery === currentQuery) {
                delete this.fetchError;
            }
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
}

export default angular
    .module('pages.organization.projects', [])
    .controller('OrganizationProjectsController', OrganizationProjectsController);

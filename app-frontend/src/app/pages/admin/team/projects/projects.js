import angular from 'angular';
import _ from 'lodash';

class Controller {
    constructor(
        $scope, $stateParams, $log, $window,
        modalService, projectService, teamService, authService, paginationService,
        platform, organization, members, team
    ) {
        'ngInject';
        $scope.autoInject(this, arguments);
    }

    $onInit() {
        this.loading = false;
        this.searchTerm = '';
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
                groupType: 'team',
                groupId: this.team.id,
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

const Module = angular.module('admin.team.projects', []);
Module.controller('TeamProjectsController', Controller);

export default Module;

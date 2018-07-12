import angular from 'angular';
import _ from 'lodash';

class UserProjectsController {
    constructor(
        $scope, $stateParams, $log, $window,
        modalService, authService, projectService, paginationService,
        organizations, teams
    ) {
        'ngInject';
        $scope.autoInject(this, arguments);
    }

    $onInit() {
        this.loading = false;
        this.searchTerm = '';
        this.onSearch = this.paginationService.buildPagedSearch(this);

        this.fetchPage();
    }

    fetchPage(page = this.$stateParams.page || 1) {
        this.loading = true;
        this.projectService.query(
            {
                sort: 'createdAt,desc',
                pageSize: 10,
                ownershipType: 'owned',
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

const Module = angular.module('pages.user.projects', []);
Module.controller('UserProjectsController', UserProjectsController);

export default Module;

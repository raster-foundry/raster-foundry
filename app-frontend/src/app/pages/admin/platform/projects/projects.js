import angular from 'angular';
import _ from 'lodash';

class PlatformProjectsController {
    constructor(
        $scope, $state, $stateParams, $log, $window,
        modalService, authService, projectService, paginationService,
        platform, organizations, members
    ) {
        'ngInject';
        $scope.autoInject(this, arguments);
    }

    $onInit() {
        this.pagination = {};
        this.loading = false;
        this.searchTerm = '';
        this.isEffectiveAdmin = this.authService.isEffectiveAdmin(this.platform.id);
        this.onSearch = this.paginationService.buildPagedSearch(this);

        this.fetchPage();
    }

    fetchPage(page = this.$stateParams.page || 1) {
        this.loading = true;
        this.projectService.query(
            {
                sort: 'createdAt,desc',
                pageSize: 1,
                ownershipType: 'inherited',
                groupType: 'platform',
                groupId: this.platform.id,
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

const Module = angular.module('pages.platform.projects', []);
Module.controller('PlatformProjectsController', PlatformProjectsController);

export default Module;

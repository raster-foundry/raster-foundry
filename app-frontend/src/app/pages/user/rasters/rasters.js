import angular from 'angular';
import _ from 'lodash';

class Controller {
    constructor(
        $scope, $stateParams, $log, $window,
        modalService, organizationService, teamService, authService, paginationService,
        RasterFoundryRepository, sceneService, teams, organizations, user
    ) {
        'ngInject';
        $scope.autoInject(this, arguments);
    }

    $onInit() {
        this.loading = false;
        this.searchTerm = '';
        this.onSearch = this.paginationService.buildPagedSearch(this);

        this.repository = {
            name: 'Raster Foundry',
            service: this.RasterFoundryRepository
        };

        this.fetchPage();
    }

    fetchPage(page = this.$stateParams.page || 1) {
        this.loading = true;
        this.sceneService.query(
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

const Module = angular.module('pages.user.rasters', []);
Module.controller('UserRastersController', Controller);

export default Module;

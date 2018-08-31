import angular from 'angular';
import _ from 'lodash';

class Controller {
    constructor(
        $scope, $state, $log, $window,
        modalService, organizationService, teamService, authService, paginationService,
        RasterFoundryRepository, sceneService, teams, organizations, user
    ) {
        'ngInject';
        $scope.autoInject(this, arguments);
    }

    $onInit() {
        this.repository = {
            name: 'Raster Foundry',
            service: this.RasterFoundryRepository
        };

        this.fetchPage();
    }

    fetchPage(page = this.$state.params.page || 1, search = this.$state.params.search) {
        this.search = search && search.length ? search : null;
        delete this.fetchError;
        this.results = [];
        const currentQuery = this.sceneService.query(
            {
                sort: 'createdAt,desc',
                pageSize: 10,
                ownershipType: 'owned',
                page: page - 1,
                search: this.search
            }
        ).then(paginatedResponse => {
            this.results = paginatedResponse.results;
            this.pagination = this.paginationService.buildPagination(paginatedResponse);
            this.paginationService.updatePageParam(page, search);
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

const Module = angular.module('pages.user.rasters', []);
Module.controller('UserRastersController', Controller);

export default Module;

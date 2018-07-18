import angular from 'angular';
import _ from 'lodash';

class Controller {
    constructor(
        $scope, $stateParams, $log, $window,
        modalService, organizationService, teamService, authService,
        RasterFoundryRepository, sceneService, paginationService,
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

        this.debouncedSearch = _.debounce(
            this.onSearch.bind(this),
            500,
            {leading: false, trailing: true}
        );

        this.repository = {
            name: 'Raster Foundry',
            service: this.RasterFoundryRepository
        };

        this.fetchPage();
    }

    onSearch(search) {
        this.searchTerm = search;
        this.fetchPage();
    }

    fetchPage(page = this.$stateParams.page || 1) {
        this.loading = true;
        this.sceneService.query(
            {
                sort: 'createdAt,desc',
                pageSize: 10,
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

const Module = angular.module('pages.platform.rasters', []);
Module.controller('PlatformRastersController', Controller);

export default Module;

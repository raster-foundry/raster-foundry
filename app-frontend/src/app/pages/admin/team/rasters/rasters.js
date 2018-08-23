import angular from 'angular';
import _ from 'lodash';

class Controller {
    constructor(
        $scope, $state, $log, $window,
        modalService, organizationService, teamService, authService,
        RasterFoundryRepository, sceneService, paginationService,
        platform, organization, members, team
    ) {
        'ngInject';
        $scope.autoInject(this, arguments);
    }

    $onInit() {
        this.repository = {
            name: 'Raster Foundry',
            service: this.RasterFoundryRepository
        };

        this.isEffectiveAdmin = this.authService.isEffectiveAdmin([
            this.platform.id,
            this.organization.id
        ]);

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
                ownershipType: 'inherited',
                groupType: 'team',
                groupId: this.team.id,
                search: this.search,
                page: page - 1
            }
        ).then(paginatedResponse => {
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

const Module = angular.module('pages.team.rasters', []);
Module.controller('TeamRastersController', Controller);

export default Module;

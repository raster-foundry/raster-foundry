import angular from 'angular';
import _ from 'lodash';

class Controller {
    constructor(
        $scope, $stateParams, $log, $window,
        modalService, organizationService, teamService, authService,
        RasterFoundryRepository, sceneService, teams, organizations, user
    ) {
        this.$scope = $scope;
        this.$stateParams = $stateParams;
        this.$log = $log;
        this.$window = $window;
        this.modalService = modalService;
        this.organizationService = organizationService;
        this.teamService = teamService;
        this.authService = authService;
        this.repository = {
            name: 'Raster Foundry',
            service: RasterFoundryRepository
        };
        this.sceneService = sceneService;

        this.teams = teams;
        this.organizations = organizations;
        this.user = user;
    }

    $onInit() {
        this.debouncedSearch = _.debounce(
            this.onSearch.bind(this),
            500,
            {leading: false, trailing: true}
        );

        this.fetchPage();
    }

    onSearch(search) {
        this.fetchPage(0, search);
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


    fetchPage(page = 0, search = '') {
        this.loading = true;
        this.sceneService.query(
            {
                sort: 'createdAt,desc',
                pageSize: 10,
                ownershipType: 'owned',
                page
            }
        ).then(paginatedResponse => {
            this.rasters = paginatedResponse.results;
            this.updatePagination(paginatedResponse);
        }).finally(() => {
            this.loading = false;
        });
    }


    buildOptions() {
        this.items.forEach(obj => {
            Object.assign(obj, {
                options: {
                    items: this.buildOptions(obj)
                },
                showOptions: this.isEffectiveAdmin
            });
        });
    }

    buildOptions(obj) {
        return [];
    }
}

const Module = angular.module('pages.user.rasters', []);
Module.controller('UserRastersController', Controller);

export default Module;

import angular from 'angular';
import _ from 'lodash';

class OrganizationRastersController {
    constructor(
        $scope, $stateParams, $log, $window,
        modalService, organizationService, teamService, authService,
        RasterFoundryRepository, sceneService,
        platform, organization, members, teams
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

        this.platform = platform;
        this.organization = organization;
        this.members = members;
        this.teams = teams;
    }

    $onInit() {
        this.debouncedSearch = _.debounce(
            this.onSearch.bind(this),
            500,
            {leading: false, trailing: true}
        );

        this.isEffectiveAdmin = this.authService.isEffectiveAdmin([
            this.platform.id,
            this.organization.id
        ]);

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
                ownershipType: 'inherited',
                groupType: 'organization',
                groupId: this.organization.id,
                search,
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

const OrganizationRastersModule = angular.module('pages.organization.rasters', []);
OrganizationRastersModule
    .controller('OrganizationRastersController', OrganizationRastersController);

export default OrganizationRastersModule;

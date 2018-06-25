import angular from 'angular';
import _ from 'lodash';

class OrganizationProjectsController {
    constructor(
        $scope, $stateParams, $log, $window,
        modalService, authService, projectService,
        platform, organization, members, teams
    ) {
        this.$scope = $scope;
        this.$stateParams = $stateParams;
        this.$log = $log;
        this.$window = $window;
        this.modalService = modalService;
        this.authService = authService;
        this.projectService = projectService;

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
        this.projectService.query(
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
            this.projects = paginatedResponse.results;
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

const OrganizationProjectsModule = angular.module('pages.organization.projects', []);

OrganizationProjectsModule
    .controller('OrganizationProjectsController', OrganizationProjectsController);

export default OrganizationProjectsModule;

import angular from 'angular';
import _ from 'lodash';

class UserProjectsController {
    constructor(
        $stateParams, $log, $window,
        modalService, authService, projectService,
        organizations, teams
    ) {
        this.$stateParams = $stateParams;
        this.$log = $log;
        this.$window = $window;
        this.modalService = modalService;
        this.authService = authService;
        this.projectService = projectService;
        this.organizations = organizations;
        this.teams = teams;
    }

    $onInit() {
        this.projects = [];
        this.loading = false;

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


    fetchPage(page = 0) {
        this.loading = true;
        this.projectService.query(
            {
                sort: 'createdAt,desc',
                pageSize: 10,
                ownershipType: 'owned',
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

    buildOptions() {
        return [];
    }
}

const Module = angular.module('pages.user.projects', []);
Module.controller('UserProjectsController', UserProjectsController);

export default Module;

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
        this.isEffectiveAdmin = this.authService.isEffectiveAdmin(this.platform.id);
        this.fetchPage();
    }

    fetchPage(page = this.$state.page || 1, search = this.$state.params.search) {
        this.search = search && search.length ? search : null;
        delete this.fetchError;
        this.results = [];
        const currentQuery = this.projectService.query(
            {
                sort: 'createdAt,desc',
                pageSize: 10,
                ownershipType: 'inherited',
                groupType: 'platform',
                groupId: this.platform.id,
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

const Module = angular.module('pages.platform.projects', []);
Module.controller('PlatformProjectsController', PlatformProjectsController);

export default Module;

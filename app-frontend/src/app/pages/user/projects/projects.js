/* global HELPCONFIG */
import angular from 'angular';
import _ from 'lodash';

class UserProjectsController {
    constructor(
        $scope, $state, $log, $window,
        modalService, authService, projectService, paginationService,
        organizations, teams
    ) {
        'ngInject';
        $scope.autoInject(this, arguments);
        this.HELPCONFIG = HELPCONFIG;
    }

    $onInit() {
        this.fetchPage();
    }

    fetchPage(page = this.$state.params.page || 1, search = this.$state.params.search) {
        this.search = search && search.length ? search : null;
        delete this.fetchError;
        this.results = [];
        const currentQuery = this.projectService.query(
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

const Module = angular.module('pages.user.projects', []);
Module.controller('UserProjectsController', UserProjectsController);

export default Module;

import angular from 'angular';
import projectSelectModalTpl from './projectSelectModal.html';

const ProjectSelectModalComponent = {
    templateUrl: projectSelectModalTpl,
    bindings: {
        close: '&',
        dismiss: '&',
        modalInstance: '<',
        resolve: '<'
    },
    controller: 'ProjectSelectModalController'
};

class ProjectSelectModalController {
    constructor($log, projectService, paginationService) {
        'ngInject';
        this.projectService = projectService;
        this.paginationService = paginationService;
    }

    $onInit() {
        // Can be one of {owned, shared}
        this.currentOwnershipFilter = 'owned';
        this.fetchPage();
    }

    fetchPage(page = 1, search = this.search) {
        if (this.resolve.isLayerSelect) {
            this.fetchPageLayers(page);
        } else {
            this.fetchPageProjects(page, search);
        }
    }

    fetchPageLayers(page) {
        delete this.fetchError;
        this.results = [];
        const currentQuery = this.projectService
            .getProjectLayers(this.resolve.project.id, {
                sort: 'createdAt,desc',
                pageSize: 9,
                page: page - 1
            })
            .then(
                paginatedResponse => {
                    this.results = paginatedResponse.results;
                    this.pagination = this.paginationService.buildPagination(paginatedResponse);
                    this.paginationService.updatePageParam(page, this.search);
                    if (this.currentQuery === currentQuery) {
                        delete this.fetchError;
                    }
                },
                e => {
                    if (this.currentQuery === currentQuery) {
                        this.fetchError = e;
                    }
                }
            )
            .finally(() => {
                if (this.currentQuery === currentQuery) {
                    delete this.currentQuery;
                }
            });
        this.currentQuery = currentQuery;
    }

    fetchPageProjects(page, search) {
        this.search = search && search.length ? search : null;
        delete this.fetchError;
        this.results = [];
        const currentQuery = this.projectService
            .query({
                sort: 'createdAt,desc',
                pageSize: 9,
                page: page - 1,
                ownershipType: this.currentOwnershipFilter,
                search: this.search
            })
            .then(
                paginatedResponse => {
                    this.results = paginatedResponse.results;
                    this.pagination = this.paginationService.buildPagination(paginatedResponse);
                    this.paginationService.updatePageParam(page, this.search);
                    if (this.currentQuery === currentQuery) {
                        delete this.fetchError;
                    }
                },
                e => {
                    if (this.currentQuery === currentQuery) {
                        this.fetchError = e;
                    }
                }
            )
            .finally(() => {
                if (this.currentQuery === currentQuery) {
                    delete this.currentQuery;
                }
            });
        this.currentQuery = currentQuery;
    }

    setSelected(project) {
        this.close({ $value: project });
    }

    handleOwnershipFilterChange(newFilterValue) {
        this.fetchPage(1);
    }
}

const ProjectSelectModalModule = angular.module('components.projects.projectSelectModal', []);

ProjectSelectModalModule.controller('ProjectSelectModalController', ProjectSelectModalController);
ProjectSelectModalModule.component('rfProjectSelectModal', ProjectSelectModalComponent);

export default ProjectSelectModalModule;

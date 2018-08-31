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
        this.fetchPage();
    }

    fetchPage(page = 1, search = this.search) {
        this.search = search && search.length ? search : null;
        delete this.fetchError;
        this.results = [];
        const currentQuery = this.projectService.query(
            {
                sort: 'createdAt,desc',
                pageSize: 5,
                page: page - 1,
                search: this.search
            }
        ).then((paginatedResponse) => {
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

    setSelected(project) {
        this.close({$value: project});
    }
}

const ProjectSelectModalModule = angular.module('components.projects.projectSelectModal', []);

ProjectSelectModalModule.controller(
    'ProjectSelectModalController', ProjectSelectModalController
);
ProjectSelectModalModule.component(
    'rfProjectSelectModal', ProjectSelectModalComponent
);

export default ProjectSelectModalModule;

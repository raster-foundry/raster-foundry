import angular from 'angular';
import projectLayerSelectModalTpl from './index.html';

class ProjectLayerSelectModalController {
    constructor($log, projectService, paginationService) {
        'ngInject';
        this.projectService = projectService;
        this.paginationService = paginationService;
    }

    $onInit() {
        this.fetchPage();
    }

    fetchPage(page = 1) {
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

    setSelected(project) {
        this.close({ $value: project });
    }
}

const ProjectLayerSelectModalComponent = {
    templateUrl: projectLayerSelectModalTpl,
    bindings: {
        close: '&',
        dismiss: '&',
        modalInstance: '<',
        resolve: '<'
    },
    controller: 'ProjectLayerSelectModalController'
};

export default angular
    .module('components.projects.projectLayerSelectModal', [])
    .controller('ProjectLayerSelectModalController', ProjectLayerSelectModalController)
    .component('rfProjectLayerSelectModal', ProjectLayerSelectModalComponent).name;

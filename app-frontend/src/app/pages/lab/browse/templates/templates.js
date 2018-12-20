/* global _ */

class LabBrowseTemplatesController {
    constructor( // eslint-disable-line max-params
        $log, $scope, $state,
        analysisService, modalService, paginationService,
        user, platform
    ) {
        'ngInject';
        $scope.autoInject(this, arguments);
    }

    $onInit() {
        this.currentOwnershipFilter = 'owned';
        this.fetchPage();
    }

    fetchPage(page = this.$state.params.page || 1, search = this.$state.params.search) {
        this.search = search && search.length ? search : null;
        delete this.fetchError;
        this.results = [];
        let currentQuery = this.analysisService.fetchTemplates({
            sort: 'createdAt,desc',
            pageSize: 10,
            page: page - 1,
            search: this.search,
            ownershipType: this.currentOwnershipFilter
        }).then(paginatedResponse => {
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
  
    onTemplateDelete(templateId) {
        this.analysisService.deleteTemplate(templateId).then(() => {
            this.fetchPage();
        }, err => {
            this.$log.error(`There is an error deleting template ${templateId}`, err);
        });
    }

    handleOwnershipFilterChange(newFilterValue) {
        this.fetchPage(1);
    }

    onTemplateShareClick(template) {
        this.modalService.open({
            component: 'rfPermissionModal',
            resolve: {
                object: () => template,
                permissionsBase: () => 'tools',
                objectType: () => 'TEMPLATE',
                objectName: () => template.name,
                platform: () => this.platform
            }
        });
    }
}

export default angular.module('pages.lab.browse.results', [])
    .controller('LabBrowseTemplatesController', LabBrowseTemplatesController);

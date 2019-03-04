/* global _ BUILDCONFIG */

class LabBrowseTemplatesController {
    constructor( // eslint-disable-line max-params
        $log, $scope, $state,
        analysisService, modalService, paginationService, authService,
        platform
    ) {
        'ngInject';
        $scope.autoInject(this, arguments);
        this.BUILDCONFIG = BUILDCONFIG;
    }

    $onInit() {
        this.currentOwnershipFilter = this.$state.params.ownership || '';
        this.fetchPage();
    }

    fetchPage(page = this.$state.params.page || 1, search = this.$state.params.search) {
        this.search = search && search.length ? search : null;
        delete this.fetchError;
        this.results = [];
        const queryParams = {
            sort: 'createdAt,desc',
            pageSize: 10,
            page: page - 1,
            search: this.search
        };
        if (!this.currentOwnershipFilter) {
            queryParams.owner = this.BUILDCONFIG.PLATFORM_USERS;
        } else if (this.currentOwnershipFilter !== 'all') {
            queryParams.ownershipType = this.currentOwnershipFilter;
        }

        let currentQuery = this.analysisService
            .fetchTemplates(queryParams)
            .then(paginatedResponse => {
                this.results = paginatedResponse.results;
                this.pagination = this.paginationService.buildPagination(paginatedResponse);
                this.paginationService.updatePageParam(page, this.search, null, {
                    ownership: this.currentOwnershipFilter
                });
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

    onTemplateEditClick(template) {
        this.modalService.open({
            component: 'rfTemplateCreateModal',
            resolve: {
                existingTemplate: () => template
            }
        }).result.then( data => {
            let idx = this.results.findIndex( tpl => tpl.id === data.id );
            this.results[idx] = data;
        });
    }
}

export default angular.module('pages.lab.browse.results', [])
    .controller('LabBrowseTemplatesController', LabBrowseTemplatesController);

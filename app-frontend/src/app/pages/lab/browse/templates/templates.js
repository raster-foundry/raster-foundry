/* global _ */

class LabBrowseTemplatesController {
    constructor( // eslint-disable-line max-params
        $log, $scope, $state, analysisService, modalService, paginationService
    ) {
        'ngInject';
        $scope.autoInject(this, arguments);
    }

    $onInit() {
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
            search: this.search
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

    // initFilters() {
    //     this.queryParams = _.mapValues(this.$state.params, v => {
    //         return v || null;
    //     });
    //     this.filters = Object.assign({}, this.queryParams);
    // }

    // toggleTag(index) {
    //     this.tags[index].selected = !this.tags[index].selected;
    // }

    // toggleCategory(index) {
    //     this.categories[index].selected = !this.categories[index].selected;
    // }

    openTemplateCreateModal() {
        this.modalService.open({
            component: 'rfTemplateCreateModal'
        });
    }
}

export default angular.module('pages.lab.browse.results', [])
    .controller('LabBrowseTemplatesController', LabBrowseTemplatesController);

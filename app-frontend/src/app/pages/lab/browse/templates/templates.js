/* global _ */

class LabBrowseTemplatesController {
    constructor( // eslint-disable-line max-params
        $log, $scope, $state, analysisService, modalService
    ) {
        'ngInject';
        this.analysisService = analysisService;
        this.$scope = $scope;
        this.$state = $state;
        this.$log = $log;
        this.modalService = modalService;
    }

    $onInit() {
        this.initFilters();
        this.initSearchTerms();
        this.fetchTemplateList(this.$state.params.page);
        this.searchString = '';
    }

    initFilters() {
        this.queryParams = _.mapValues(this.$state.params, v => {
            return v || null;
        });
        this.filters = Object.assign({}, this.queryParams);
    }

    initSearchTerms() {
        this.searchTerms = [];
        if (this.queryParams.query) {
            this.searchTerms = this.queryParams.query.trim().split(' ');
        }
    }

    fetchTemplateList(page = 1) {
        this.loadingTemplates = true;
        this.analysisService.fetchTemplates(
            {
                pageSize: 12,
                page: page - 1,
                sort: 'createdAt,desc'
            }
        ).then(d => {
            this.currentPage = page;
            this.updatePagination(d);
            let replace = !this.$state.params.page;
            this.$state.transitionTo(
                this.$state.$current.name,
                {page: this.currentPage},
                {
                    location: replace ? 'replace' : true,
                    notify: false
                }
            );
            this.lastTemplateResponse = d;
            this.templateList = d.results;
            this.loadingTemplates = false;
        });
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

    removeSearchTerm(index) {
        this.searchTerms.splice(index, 1);
        this.search();
    }

    clearSearch() {
        this.searchTerms = [];
        this.search();
    }

    search(value) {
        this.searchString = value;
        if (this.searchString) {
            this.analysisService.searchQuery().then(templates => {
                this.templateList = templates;
            });
        } else {
            this.fetchTemplateList();
        }
    }

    toggleTag(index) {
        this.tags[index].selected = !this.tags[index].selected;
    }

    toggleCategory(index) {
        this.categories[index].selected = !this.categories[index].selected;
    }

    openTemplateCreateModal() {
        this.modalService.open({
            component: 'rfTemplateCreateModal'
        });
    }
}

export default angular.module('pages.lab.browse.templates', [])
    .controller('LabBrowseTemplatesController', LabBrowseTemplatesController);

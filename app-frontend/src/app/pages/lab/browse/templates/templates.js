/* global _ */

class LabBrowseTemplatesController {
    constructor( // eslint-disable-line max-params
        $log, $scope, $state, toolService, toolTagService, toolCategoryService, $uibModal
    ) {
        'ngInject';
        this.toolService = toolService;
        this.toolTagService = toolTagService;
        this.toolCategoryService = toolCategoryService;
        this.$scope = $scope;
        this.$state = $state;
        this.$log = $log;
        this.$uibModal = $uibModal;
    }

    $onInit() {
        this.initFilters();
        this.initSearchTerms();
        this.fetchToolList(this.$state.params.page);
        this.fetchToolTags();
        this.fetchToolCategories();
        this.searchString = '';

        this.$scope.$on('$destroy', () => {
            if (this.activeModal) {
                this.activeModal.dismiss();
            }
        });
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

    initSelectedToolTags() {
        this.selectedToolTags = [];
        if (this.queryParams.tooltag) {
            this.selectedToolTags = this.queryParams.tooltag.split(' ');
        }
    }

    initSelectedToolCategories() {
        this.selectedToolCategories = [];
        if (this.queryParams.toolcategory) {
            this.selectedToolCategories =
                this.queryParams.toolcategory.split(' ');
        }
    }

    fetchToolList(page = 1) {
        this.loadingTools = true;
        this.toolService.query(
            {
                pageSize: 10,
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
            this.lastToolResponse = d;
            this.toolList = d.results;
            this.loadingTools = false;
        });
    }

    fetchToolTags() {
        this.loadingToolTags = true;
        this.toolTagService.query().then(d => {
            this.lastToolTagResponse = d;
            this.processToolTags(d);
            this.loadingToolTags = false;
        });
    }

    processToolTags(data) {
        this.initSelectedToolTags();
        this.toolTagList = data.results.map(t => {
            t.selected = false;
            if (this.selectedToolTags.indexOf(t.id) >= 0) {
                t.selected = true;
            }
            return t;
        });
    }

    fetchToolCategories() {
        this.loadingToolCategories = true;
        this.toolCategoryService.query().then(d => {
            this.lastToolCategoryResponse = d;
            this.processToolCategories(d);
            this.loadingToolCategories = false;
        });
    }

    processToolCategories(data) {
        this.initSelectedToolCategories();
        this.toolCategoryList = data.results.map(c => {
            c.selected = false;
            if (this.selectedToolCategories.indexOf(c.slugLabel) >= 0) {
                c.selected = true;
            }
            return c;
        });
    }

    handleTagChange(tag) {
        const tagIndex = this.selectedToolTags.indexOf(tag.id);
        if (tagIndex >= 0) {
            this.selectedToolTags.splice(tagIndex, 1);
        } else {
            this.selectedToolTags.push(tag.id);
        }
        this.search();
    }

    handleCategoryChange(category) {
        const categoryIndex = this.selectedToolCategories.indexOf(category.slugLabel);
        if (categoryIndex >= 0) {
            this.selectedToolCategories.splice(categoryIndex, 1);
        } else {
            this.selectedToolCategories.push(category.slugLabel);
        }
        this.search();
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
            this.toolService.searchQuery().then(tools => {
                this.toolList = tools;
            });
        } else {
            this.fetchToolList();
        }
    }

    toggleTag(index) {
        this.tags[index].selected = !this.tags[index].selected;
    }

    toggleCategory(index) {
        this.categories[index].selected = !this.categories[index].selected;
    }

    openToolCreateModal() {
        if (this.activeModal) {
            this.activeModal.dismiss();
        }
        this.activeModal = this.$uibModal.open({
            component: 'rfToolCreateModal'
        });
    }
}

export default angular.module('pages.lab.browse.templates', [])
    .controller('LabBrowseTemplatesController', LabBrowseTemplatesController);

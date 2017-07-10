/* global _ */

export default class MarketSearchController {
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
        this.fetchToolList();
        this.fetchToolTags();
        this.fetchToolCategories();

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

    fetchToolList() {
        this.loadingTools = true;
        this.toolService.query(
            this.queryParams
        ).then(d => {
            this.updatePagination(d);
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

    search() {
        this.$state.go('market.search', {
            query: this.searchTerms.join(' '),
            toolcategory: this.selectedToolCategories.join(' '),
            tooltag: this.selectedToolTags.join(' ')
        });
    }

    toggleTag(index) {
        this.tags[index].selected = !this.tags[index].selected;
    }

    toggleCategory(index) {
        this.categories[index].selected = !this.categories[index].selected;
    }

    navTool(tool) {
        this.$state.go('market.tool', {id: tool.id, toolData: tool});
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

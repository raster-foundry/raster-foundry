/* global BUILDCONFIG */
class LabBrowseAnalysesController {
    constructor($state, analysisService, authService, localStorage) {
        'ngInject';
        this.$state = $state;
        this.analysisService = analysisService;
        this.authService = authService;
        this.localStorage = localStorage;
    }

    $onInit() {
        this.BUILDCONFIG = BUILDCONFIG;
        this.defaultSortingDirection = 'desc';
        this.defaultSortingField = 'modifiedAt';
        this.initSorting();
        this.fetchAnalysesList(this.$state.params.page);
    }

    initSorting() {
        const sortString = this.fetchSorting();
        if (sortString) {
            const sort = this.deserializeSort(sortString);
            this.sortingField = sort.field || this.defaultSortingField;
            this.sortingDirection = sort.direction || this.defaultSortingDirection;
        } else {
            this.sortingField = this.defaultSortingField;
            this.sortingDirection = this.defaultSortingDirection;
        }
    }

    fetchAnalysesList(page = 1) {
        this.loadingAnalyses = true;
        this.analysisService.fetchAnalyses(
            {
                pageSize: 10,
                page: page - 1,
                sort: this.serializeSort()
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
            this.lastAnalysisResponse = d;
            this.analysesList = d.results;
            this.loadingAnalyses = false;
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

    formatAnalysisVisibility(visibility) {
        const v = visibility.toUpperCase();
        if (v === 'PUBLIC') {
            return 'Public';
        }
        return 'Private';
    }

    serializeSort() {
        return `${this.sortingField},${this.sortingDirection}`;
    }

    deserializeSort(sortString) {
        const splitSortString = sortString.split(',');
        return {
            field: splitSortString[0],
            direction: splitSortString[1]
        };
    }

    fetchSorting() {
        const k = `${this.authService.getProfile().nickname}-analysis-sort`;
        return this.localStorage.getString(k);
    }

    storeSorting() {
        const k = `${this.authService.getProfile().nickname}-analysis-sort`;
        return this.localStorage.setString(k, this.serializeSort());
    }

    onSortChange(field) {
        if (field === this.sortingField) {
            // Toggle sorting direction if the same field is being used
            this.sortingDirection =
                this.sortingDirection === 'asc' ? 'desc' : 'asc';
        } else {
            this.sortingField = field;
            this.sortingDirection = this.defaultSortingDirection;
        }
        this.storeSorting();
        this.fetchAnalysesList(this.currentPage);
    }

    onAnalysisDelete(analysisId) {
        this.analysisService.deleteAnalysis(analysisId).then(() => {
            this.fetchAnalysesList(this.currentPage);
        });
    }
}

export default angular.module('pages.lab.browse.analyses', [])
    .controller('LabBrowseAnalysesController', LabBrowseAnalysesController);

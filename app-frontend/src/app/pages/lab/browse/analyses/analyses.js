/* global BUILDCONFIG */
import {Set} from 'immutable';
class LabBrowseAnalysesController {
    constructor(
        $scope, $state, $log,
        analysisService, authService, localStorage, modalService, exportService, paginationService
    ) {
        'ngInject';
        $scope.autoInject(this, arguments);
    }

    $onInit() {
        this.BUILDCONFIG = BUILDCONFIG;
        this.analysesExports = {};
        this.defaultSortingDirection = 'desc';
        this.defaultSortingField = 'modifiedAt';
        this.initSorting();
        this.fetchPage();
        this.selected = new Set();
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

    fetchPage(
        page = this.$state.params.page || 1,
        search = this.$state.params.search,
        sort = this.deserializeSort(this.$state.params.sort)
    ) {
        this.search = search && search.length ? search : null;
        delete this.fetchError;
        this.analyses = [];
        const currentQuery = this.analysisService.fetchAnalyses({
            pageSize: 10,
            page: page - 1,
            sort: this.serializeSort(),
            search: this.search
        }).then(paginatedResponse => {
            this.analyses = paginatedResponse.results;
            this.pagination = this.paginationService.buildPagination(paginatedResponse);
            this.paginationService.updatePageParam(page, search, this.serializeSort());
            this.checkAnalysesExports(this.analyses);
            if (this.currentQuery) {
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

    shouldShowPlaceholder() {
        return !this.currentQuery &&
            !this.fetchError &&
            (!this.search || !this.search.length) &&
            this.analyses &&
            this.analyses.length === 0;
    }

    shouldShowEmptySearch() {
        return !this.currentQuery &&
            !this.fetchError &&
            this.search && this.search.length &&
            this.analyses && !this.analyses.length;
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

    deserializeSort(sortString = `${this.defaultSortingField},${this.defaultSortingDirection}`) {
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
        this.fetchPage(this.currentPage);
    }

    deleteSelected() {
        const modal = this.modalService.open({
            component: 'rfConfirmationModal',
            resolve: {
                title: () => `Delete ${this.selected.size} analyses?`,
                content: () => 'Deleting analyses will make any ' +
                    'further tile requests with them fail',
                confirmText: () => 'Delete Analyses',
                cancelText: () => 'Cancel'
            }
        });

        modal.result.then(() => {
            // TODO: use $q.all instead
            this.selected.forEach((id) => {
                this.analysisService.deleteAnalysis(id).then(() => {
                    this.selected = this.selected.delete(id);
                    if (this.selected.size === 0) {
                        this.fetchPage();
                    }
                }, () => {
                    this.fetchPage();
                });
            });
        });
    }

    toggleAnalysisSelection(id) {
        if (this.selected.has(id)) {
            this.selected = this.selected.delete(id);
        } else {
            this.selected = this.selected.add(id);
        }
    }

    onAnalysisExportModalClick(analysis) {
        this.modalService.open({
            component: 'rfExportAnalysisDownloadModal',
            resolve: {
                analysis: () => analysis
            }
        });
    }

    checkAnalysesExports(analysesList) {
        analysesList.map(analysis => {
            this.checkAnalysisExport(analysis.id);
        });
    }

    checkAnalysisExport(analysisId) {
        this.exportService.query(
            {
                sort: 'createdAt,desc',
                pageSize: '20',
                page: 0,
                analysis: analysisId
            }
        ).then(firstPageExports => {
            if (firstPageExports.results.find(r => r.toolRunId === analysisId)) {
                this.analysesExports[analysisId] = firstPageExports.count;
            }
        });
    }
}

export default angular.module('pages.lab.browse.analyses', [])
    .controller('LabBrowseAnalysesController', LabBrowseAnalysesController);

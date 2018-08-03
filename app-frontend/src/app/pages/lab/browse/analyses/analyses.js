/* global BUILDCONFIG */
import {Set} from 'immutable';
class LabBrowseAnalysesController {
    constructor(
        $scope, $state, $log,
        analysisService, authService, localStorage, modalService, exportService
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
        this.fetchAnalysesList(this.$state.params.page);
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

            this.checkAnalysesExports(this.analysesList);
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
            this.selected.forEach((id) => {
                this.analysisService.deleteAnalysis(id).then(() => {
                    this.selected = this.selected.delete(id);
                    if (this.selected.size === 0) {
                        this.fetchAnalysesList();
                    }
                }, () => {
                    this.fetchAnalysesList();
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
                analysis: () => analysis,
                exports: () => this.analysesExports[analysis.id].results
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
                this.analysesExports[analysisId] = firstPageExports;
            }
        });
    }
}

export default angular.module('pages.lab.browse.analyses', [])
    .controller('LabBrowseAnalysesController', LabBrowseAnalysesController);

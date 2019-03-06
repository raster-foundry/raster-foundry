import tpl from './index.html';
import {colorStopsToRange, createRenderDefinition} from '_redux/histogram-utils';
import {nodesFromAst, astFromNodes} from '_redux/node-utils';
import _ from 'lodash';
import {Map, Set} from 'immutable';

class AnalysesListController {
    constructor(
        $rootScope, $scope, $state, $log, $q,
        mapService, projectService, analysisService, paginationService,
        authService, modalService
    ) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    $onInit() {
        this.visible = new Set();
        this.selected = new Map();
        this.itemActions = new Map();
        this.syncMapLayersToVisible();
        this.fetchPage();
    }

    $onDestroy() {
        this.getMap().then((map) => {
            map.deleteLayers('Project Analyses');
        });
    }

    getMap() {
        return this.mapService.getMap('project');
    }

    showFirstAnalysis() {
        this.visible = this.visible.clear();
        this.visible = this.visible.add(this.itemList[0].id);
        this.syncMapLayersToVisible();
    }

    showAllAnalyses() {
        this.itemList.forEach(item => {
            this.visible = this.visible.add(item.id);
        });
        this.syncMapLayersToVisible();
    }

    fetchPage(page = this.$state.params.page || 1) {
        this.itemList = [];
        const currentQuery = this.projectService.getProjectAnalyses(this.project.id,
            {
                pageSize: 30,
                page: page - 1
            }
        ).then((paginatedAnalyses) => {
            this.itemList = paginatedAnalyses.results;
            this.pagination = this.paginationService.buildPagination(paginatedAnalyses);
            this.paginationService.updatePageParam(page);
            if (this.currentQuery === currentQuery) {
                delete this.fetchError;
            }
            this.itemActions = new Map(this.itemList.map(a => [a.id, []]));
            this.itemList.forEach(analysis => {
                this.getAnalysisActions(analysis);
            });
        }).catch((e) => {
            if (this.currentQuery === currentQuery) {
                this.fetchError = e;
            }
        }).finally(() => {
            if (this.currentQuery === currentQuery) {
                delete this.currentQuery;
            }
        });
        this.currentQuery = currentQuery;
        return currentQuery;
    }

    getAnalysisActions(analysis) {
        this.analysisService
            .getAnalysisActions(analysis.id)
            .then(actions => {
                if (this.itemActions.has(analysis.id)) {
                    const isEditAllowed = actions.includes('*') || actions.includes('EDIT');
                    const isDeleteAllowed = actions.includes('*') || actions.includes('DELETE');
                    if (isEditAllowed) {
                        this.itemActions = this.itemActions.set(
                            analysis.id, this.createItemActions(analysis, isDeleteAllowed));
                    }
                }
            });
    }

    createItemActions(analysis, isDeleteAllowed) {
        const previewAction = {
            icons: [
                {
                    icon: 'icon-eye',
                    isActive: () => this.visible.has(analysis.id)
                }, {
                    icon: 'icon-eye-off',
                    isActive: () => !this.visible.has(analysis.id)
                }
            ],
            name: 'Preview',
            tooltip: 'Show/hide on map',
            callback: () => this.onHide(analysis.id),
            menu: false
        };
        const selectGroupAction = {
            name: 'Select group',
            callback: () => this.selectGroup(analysis),
            tooltip: 'Select all analyses which can be edited at the same time as this one.',
            menu: true
        };
        const editAction = {
            name: 'Edit',
            callback: () => {
                this.editAnalyses(analysis);
            },
            menu: true,
            separator: true
        };

        const exportJsonAction = {
            name: 'Export JSON',
            callback: () => {},
            menu: true
        };

        const settingsAction = {
            name: 'Settings',
            callback: () => this.$state.go(
                'project.analyses.settings'
            ),
            menu: true
        };

        const deleteAction = {
            name: 'Delete Analysis',
            callback: () => this.deleteProjectAnalyses(analysis),
            menu: true
        };

        const commonActions = [previewAction, editAction];

        return [
            ...!this.selected.size ? [selectGroupAction] : [],
            ...commonActions,
            ...isDeleteAllowed ? [deleteAction] : []
        ];
    }

    onHide(id) {
        if (this.visible.has(id)) {
            this.visible = this.visible.delete(id);
        } else {
            this.visible = this.visible.add(id);
        }
        this.syncMapLayersToVisible();
    }

    isVisible(id) {
        return this.visible.has(id);
    }

    mapLayerFromAnalysis(analysisId) {
        return this.analysisService.getAnalysis(analysisId).then(analysis => {
            this.analysisService.getNodeHistogram(analysisId).then(histogram => {
                let {
                    renderDefinition,
                    histogramOptions
                } = createRenderDefinition(histogram);
                let newNodeDefinition = Object.assign(
                    {}, analysis.executionParameters,
                    {
                        metadata: Object.assign({}, analysis.executionParameters.metadata, {
                            renderDefinition,
                            histogramOptions
                        })
                    }
                );
                let nodes = nodesFromAst(analysis.executionParameters);
                let updatedAnalysis = astFromNodes({analysis, nodes}, [newNodeDefinition]);
                return this.analysisService.updateAnalysis(updatedAnalysis);
            });
        }).then(() => {
            const tileUrl = this.analysisService.getAnalysisTileUrl(analysisId);
            return L.tileLayer(tileUrl, {maxZoom: 30});
        });
    }

    syncMapLayersToVisible() {
        const visibleAnalysisIds = this.visible.toArray();
        this.getMap().then(map => {
            this.$q.all(
                visibleAnalysisIds.map(this.mapLayerFromAnalysis.bind(this)),
            ).then(layers => {
                map.setLayer(
                    'Project Analyses',
                    layers,
                    true
                );
            });
        });
    }

    onToggle(id) {
        if (this.selected.has(id)) {
            this.selected = this.selected.delete(id);
        } else {
            const analysis = this.itemList.find(a => a.id === id);
            this.selected = this.selected.set(id, analysis);
        }
        this.updateSelectText();
    }

    allVisibleSelected() {
        let itemSet = new Set(
            this.itemList
                .map(l => l.id));
        return this.selected.size &&
            itemSet.intersect(this.selected.keySeq()).size === itemSet.size;
    }

    isSelectable(item) {
        if (this.selected.size) {
            const templateId = _.get(this.selected.values().next(), 'value.templateId');
            return item.templateId === templateId;
        }
        return true;
    }

    canEditSelection() {
        const templateIds = new Set(this.selected.valueSeq().map(v => v.templateId));
        return templateIds.size <= 1;
    }

    selectAll() {
        if (this.allVisibleSelected()) {
            this.selected = this.selected.clear();
        } else {
            this.selected = this.selected.merge(
                new Map(
                    this.itemList
                        .map(i => [i.id, i]))
            );
        }
        this.updateSelectText();
    }

    selectGroup(analysis) {
        const templateId = analysis.templateId;
        this.selected = new Map(
            this.itemList
                .filter(i => i.templateId === templateId)
                .map(i => [i.id, i]));
        this.updateSelectText();
    }

    updateSelectText() {
        if (this.allVisibleSelected()) {
            this.selectText = `Clear selected (${this.selected.size})`;
        } else {
            this.selectText = `Select all listed (${this.selected.size})`;
        }
    }

    editAnalyses(analysis) {
        let analyses = analysis ? [analysis] : this.selected.valueSeq().toArray();
        this.modalService.open({
            component: 'rfAnalysisEditModal',
            resolve: {
                analyses: () => analyses
            }
        }).result.then((updates) => {
            if (updates) {
                this.selected = this.selected.clear();
                this.fetchPage();
                const oldVisible = this.visible;
                this.syncMapLayersToVisible().then(() => {
                    this.visible = oldVisible;
                    this.syncMapLayersToVisible();
                });
            }
        }).catch(() => {
        });
    }

    deleteProjectAnalyses(analyses) {
        const ids = analyses.length ? analyses.map(a => a.id) : [analyses.id];
        let modal;
        if (ids.length && ids.length > 1) {
            modal = this.modalService.open({
                component: 'rfFeedbackModal',
                resolve: {
                    title: () => 'Delete analyses?',
                    subtitle: () => 'Deleting an analysis cannot be undone',
                    content: () =>
                        `<h2>You are attempting to delete ${ids.length} `
                        + 'analyses. Do you wish to continue?</h2>'
                        + '<p>Future attempts to access these '
                        + 'analyses and their exports will fail.',
                    feedbackIconType: () => 'danger',
                    feedbackIcon: () => 'icon-warning',
                    feedbackBtnType: () => 'btn-danger',
                    feedbackBtnText: () => 'Delete analysis',
                    cancelText: () => 'Cancel'
                }
            }).result;
        } else if (ids.length) {
            modal = this.modalService.open({
                component: 'rfFeedbackModal',
                resolve: {
                    title: () => 'Delete this analysis?',
                    subtitle: () => 'Deleting an analysis cannot be undone',
                    content: () =>
                        '<h2>Do you wish to continue?</h2>'
                        + '<p>Future attempts to access this '
                        + 'analysis and its exports will fail.',
                    feedbackIconType: () => 'danger',
                    feedbackIcon: () => 'icon-warning',
                    feedbackBtnType: () => 'btn-danger',
                    feedbackBtnText: () => 'Delete analysis',
                    cancelText: () => 'Cancel'
                }
            }).result;
        } else {
            const message = 'Delete action called with no analyses';
            this.$log.error(message);
            throw new Error(message);
        }
        modal.then(() => {
            const promises = ids.map((id) => this.analysisService.deleteAnalysis(id));
            this.$q.all(promises).then(() => {
                this.selected = this.selected.clear();
                this.visible = this.visible.subtract(this.selected.keySeq());
            }).catch((e) => {
                this.$log.error(e);
            }).finally(() => {
                this.fetchPage();
                this.syncMapLayersToVisible();
            });
        }).catch(() => {
            // modal closed
        });
    }

    visualizeAnalyses() {
        this.$log.log(this.selected.size);
        this.$state.go('project.analyses.visualize', {analysis: this.selected.keySeq().toArray()});
    }

    canVisualize() {
        return this.selected.size <= 2;
    }
}

const component = {
    bindings: {
        project: '<'
    },
    templateUrl: tpl,
    controller: AnalysesListController.name
};

export default angular
    .module('components.pages.project.analyses.page', [])
    .controller(AnalysesListController.name, AnalysesListController)
    .component('rfProjectAnalysesPage', component)
    .name;

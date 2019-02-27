import tpl from './index.html';
import {colorStopsToRange, createRenderDefinition} from '_redux/histogram-utils';
import {nodesFromAst, astFromNodes} from '_redux/node-utils';
import _ from 'lodash';
import {Map, Set} from 'immutable';

class AnalysesListController {
    constructor(
        $q, $rootScope, $scope, $state,
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
        const currentQuery = this.analysisService.fetchAnalyses(
            {
                pageSize: 30,
                page: page - 1,
                projectId: this.project.id
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
        const editAction = {
            name: 'Edit',
            callback: () => {
                this.editAnalyses(analysis);
                // this.$state.go('project.analysis', {
                //     analysisId: [analysis.id],
                //     analysis: [analysis]
                // });
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
            callback: () => this.deleteProjectAnalysis(analysis),
            menu: true
        };

        let actions = [previewAction, editAction];

        if (isDeleteAllowed) {
            actions.push(deleteAction);
        }

        return actions;
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
        }).then( () => {
            const tileUrl = this.analysisService.getAnalysisTileUrl(analysisId);
            return L.tileLayer(tileUrl, {maxZoom: 30});
        });
    }

    syncMapLayersToVisible() {
        const visibleAnalysisIds = this.visible.toArray();
        this.getMap().then(map => {
            this.$q.all(
                visibleAnalysisIds.map(this.mapLayerFromAnalysis.bind(this)),
            ).then( layers => {
                map.setLayer(
                    'Project Analyses',
                    layers,
                    true
                );
            });
        });
    }

    deleteProjectAnalysis(analysis) {
        const modal = this.modalService.open({
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
        });
        modal.result.then(() => {
            this.analysisService.deleteAnalysis(analysis.id).then(() => {
                this.fetchPage();
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
        const templateId = _.get(this.selected.values().next(), 'value.templateId');
        let itemSet = new Set(
            this.itemList
                .filter(i => i.templateId === templateId)
                .map(l => l.id));
        return this.selected.size &&
            itemSet.intersect(this.selected.keySeq()).size === itemSet.size;
    }

    selectAll() {
        if (this.allVisibleSelected()) {
            this.selected = this.selected.clear();
        } else {
            const templateId = _.get(this.selected.values().next(), 'value.templateId');
            this.selected = this.selected.merge(
                new Map(
                    this.itemList
                        .filter(i => i.templateId === templateId)
                        .map(i => [i.id, i]))
            );
        }
        this.updateSelectText();
    }

    updateSelectText() {
        if (this.allVisibleSelected()) {
            this.selectText = `Clear selected (${this.selected.size})`;
        } else {
            this.selectText = `Select all listed (${this.selected.size})`;
        }
    }

    isSelectable(item) {
        if (this.selected.size) {
            const templateId = _.get(this.selected.values().next(), 'value.templateId');
            return item.templateId === templateId;
        }
        return true;
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

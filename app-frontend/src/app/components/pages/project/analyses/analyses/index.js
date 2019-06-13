/* global BUILDCONFIG */
import tpl from './index.html';
import { colorStopsToRange, createRenderDefinition } from '_redux/histogram-utils';
import { nodesFromAst, astFromNodes } from '_redux/node-utils';
import { get, find, findIndex, reject, debounce } from 'lodash';
import { Map, Set } from 'immutable';

const visibleLayerName = 'Project Analyses';
const focusedLayerName = 'Selected Analysis';

class AnalysesListController {
    constructor(
        $rootScope,
        $scope,
        $state,
        $log,
        $q,
        mapService,
        projectService,
        analysisService,
        paginationService,
        authService,
        modalService
    ) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    $onInit() {
        this.visible = new Set();
        this.selected = new Map();
        this.itemActions = new Map();
        this.syncMapLayersToVisible();
        this.fetchPage().then(() => {
            if (get(this, 'itemList.length')) {
                this.showFirstAnalysis();
            }
        });

        this.onAnalysisClickDebounced = debounce(this.onAnalysisClick.bind(this), 1000, {
            leading: true
        });
    }

    $onDestroy() {
        this.getMap().then(map => {
            map.deleteLayers(visibleLayerName);
            map.deleteLayers(focusedLayerName);
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
        const currentQuery = this.analysisService
            .fetchAnalyses({
                projectId: this.project.id,
                pageSize: 30,
                page: page - 1
            })
            .then(paginatedAnalyses => {
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
            })
            .catch(e => {
                if (this.currentQuery === currentQuery) {
                    this.fetchError = e;
                }
            })
            .finally(() => {
                if (this.currentQuery === currentQuery) {
                    delete this.currentQuery;
                }
            });
        this.currentQuery = currentQuery;
        return currentQuery;
    }

    getAnalysisActions(analysis) {
        this.analysisService.getAnalysisActions(analysis.id).then(actions => {
            if (this.itemActions.has(analysis.id)) {
                const isEditAllowed = actions.includes('*') || actions.includes('EDIT');
                const isDeleteAllowed = actions.includes('*') || actions.includes('DELETE');
                if (isEditAllowed) {
                    this.itemActions = this.itemActions.set(
                        analysis.id,
                        this.createItemActions(analysis, isDeleteAllowed)
                    );
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
                },
                {
                    icon: 'icon-eye-off',
                    isActive: () => !this.visible.has(analysis.id)
                }
            ],
            name: 'Preview',
            tooltip: 'Show/hide on map',
            callback: () => this.onHide(analysis.id),
            menu: false
        };
        const goToAnalysisAction = {
            icon: 'icon-map',
            name: 'View on map',
            tooltip: 'View layer on map',
            callback: () => this.viewAnalysisOnMap(analysis),
            menu: false
        };
        const disabledGoToAction = {
            icon: 'icon-map color-light',
            name: 'View on map',
            tooltip: 'Analysis does not have an area defined to go to',
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
            callback: () => this.$state.go('project.analyses.settings'),
            menu: true
        };

        const deleteAction = {
            name: 'Delete Analysis',
            callback: () => this.deleteProjectAnalyses(analysis),
            menu: true
        };

        const commonActions = [
            previewAction,
            ...(get(analysis, 'layerGeometry.type') ? [goToAnalysisAction] : [disabledGoToAction]),
            editAction
        ];

        return [
            ...(!this.selected.size ? [selectGroupAction] : []),
            ...commonActions,
            ...(isDeleteAllowed ? [deleteAction] : [])
        ];
    }

    viewAnalysisOnMap(analysis) {
        this.getMap().then(map => {
            let bounds = L.geoJSON(analysis.layerGeometry).getBounds();
            map.map.fitBounds(bounds);
            this.visible = new Set([analysis.id]);
            this.syncMapLayersToVisible();
        });
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
        return this.analysisService
            .getAnalysis(analysisId)
            .then(this.autoGenerateRenderDef.bind(this))
            .then(() => {
                const tileUrl = this.analysisService.getAnalysisTileUrl(analysisId);
                return L.tileLayer(tileUrl, { maxNativeZoom: BUILDCONFIG.TILES_MAX_ZOOM });
            });
    }

    autoGenerateRenderDef(analysis) {
        if (
            !get(analysis, 'executionParameters.metadata.manualRenderDefinition') ||
            !get(analysis, 'executionParameters.metadata.renderDefinition')
        ) {
            return this.analysisService.getNodeHistogram(analysis.id).then(histogram => {
                const { renderDefinition, histogramOptions } = createRenderDefinition(histogram);
                const newNodeDefinition = Object.assign({}, analysis.executionParameters, {
                    metadata: Object.assign({}, analysis.executionParameters.metadata, {
                        renderDefinition,
                        histogramOptions
                    })
                });
                const nodes = nodesFromAst(analysis.executionParameters);
                const updatedAnalysis = astFromNodes({ analysis, nodes }, [newNodeDefinition]);
                return this.analysisService.updateAnalysis(updatedAnalysis).then(res => {
                    this.updateAnalysisInPlace(updatedAnalysis);
                    return updatedAnalysis;
                });
            });
        }
        return this.$q.resolve();
    }

    syncMapLayersToVisible() {
        const visibleAnalysisIds = this.visible.toArray();
        return this.getMap().then(map => {
            return this.$q
                .all({
                    layers: this.$q.all(
                        (this.focused
                            ? reject(visibleAnalysisIds, i => i === this.focused.id)
                            : visibleAnalysisIds
                        ).map(this.mapLayerFromAnalysis.bind(this))
                    ),
                    focusedLayer: this.focused
                        ? this.mapLayerFromAnalysis(this.focused.id)
                        : this.$q.resolve(null)
                })
                .then(({ layers, focusedLayer }) => {
                    if (layers && layers.length) {
                        map.setLayer(visibleLayerName, layers, true);
                    } else {
                        map.deleteLayers(visibleLayerName);
                    }
                    if (focusedLayer) {
                        map.setLayer(focusedLayerName, focusedLayer, true);
                    } else {
                        map.deleteLayers(focusedLayerName);
                    }
                })
                .catch(err => {
                    this.layerError = err;
                    this.$log.error(
                        'There was an error fetching analysis layer information. ',
                        "Please verify that the parameters you've set are valid.",
                        err
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
        let itemSet = new Set(this.itemList.map(l => l.id));
        return (
            this.selected.size && itemSet.intersect(this.selected.keySeq()).size === itemSet.size
        );
    }

    isSelectable(item) {
        if (this.selected.size) {
            const templateId = get(this.selected.values().next(), 'value.templateId');
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
            this.selected = this.selected.merge(new Map(this.itemList.map(i => [i.id, i])));
        }
        this.updateSelectText();
    }

    selectGroup(analysis) {
        const templateId = analysis.templateId;
        this.selected = new Map(
            this.itemList.filter(i => i.templateId === templateId).map(i => [i.id, i])
        );
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
        this.modalService
            .open({
                component: 'rfAnalysisEditModal',
                resolve: {
                    analyses: () => analyses
                }
            })
            .result.then(updates => {
                if (updates) {
                    this.selected = this.selected.clear();
                    this.fetchPage();
                    const oldVisible = this.visible;
                    this.syncMapLayersToVisible().then(() => {
                        this.visible = oldVisible;
                        this.syncMapLayersToVisible();
                    });
                    if (this.focused && analyses.map(a => a.id).includes(this.focused.id)) {
                        this.focused = find(this.itemList, i => i.id === this.focused.id);
                        this.updateQuickEditState();
                    }
                }
            })
            .catch(() => {});
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
                        `<h2>You are attempting to delete ${ids.length} ` +
                        'analyses. Do you wish to continue?</h2>' +
                        '<p>Future attempts to access these ' +
                        'analyses and their exports will fail.',
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
                        '<h2>Do you wish to continue?</h2>' +
                        '<p>Future attempts to access this ' +
                        'analysis and its exports will fail.',
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
        modal
            .then(() => {
                const promises = ids.map(id => this.analysisService.deleteAnalysis(id));
                this.$q
                    .all(promises)
                    .then(() => {
                        this.selected = this.selected.clear();
                        if (this.focused && ids.includes(this.focused.id)) {
                            delete this.focused;
                            this.updateQuickEditState();
                        }
                        this.visible = this.visible.subtract(this.selected.keySeq());
                    })
                    .catch(e => {
                        this.$log.error(e);
                    })
                    .finally(() => {
                        this.fetchPage();
                        this.syncMapLayersToVisible();
                    });
            })
            .catch(() => {
                // modal closed
            });
    }

    visualizeAnalyses() {
        this.$state.go('project.analyses.visualize', {
            analysis: this.selected.keySeq().toArray()
        });
    }

    canVisualize() {
        return this.selected.size <= 3;
    }

    updateQuickEditState() {
        if (this.focused) {
            this.$state.go('project.analyses.quickedit', {
                analysis: this.focused,
                onAnalysisUpdate: this.updateAnalysis.bind(this)
            });
        } else {
            this.$state.go('project.analyses');
        }
    }

    updateAnalysisInPlace(analysis) {
        const index = findIndex(this.itemList, item => item.id === analysis.id);
        if (Number.isFinite(index)) {
            const oldAnalysis = this.itemList[index];
            const updatedAnalysis = Object.assign({}, oldAnalysis, {
                executionParameters: analysis.executionParameters
            });
            this.itemList[index] = updatedAnalysis;
        }
    }

    updateAnalysis(analysis) {
        // update analysis in place so it doesn't remove extra state that's added to the objects
        // like the template name as subtitle, layer checkbox color border, etc
        this.updateAnalysisInPlace(analysis);
        // refresh map by recreating the layers
        return this.syncMapLayersToVisible().then(() => {
            return find(this.itemList, item => item.id === analysis.id);
        });
    }

    onAnalysisClick(analysis) {
        if (analysis && this.focused && analysis.id === this.focused.id) {
            delete this.focused;
        } else if (analysis) {
            this.focused = analysis;
        } else {
            throw new Error('Analysis click event called without an analysis');
        }
        // update map layers
        this.syncMapLayersToVisible()
            .then(() => {
                // if analysis updated, update this.focused
                const foundAnalysis = find(this.itemList, item => item.id === analysis.id);
                if (foundAnalysis !== analysis && this.focused) {
                    this.focused = foundAnalysis;
                }
            })
            .finally(() => {
                // open / close analysis
                this.updateQuickEditState();
            });
    }

    zoomToSelected() {
        const geoms = this.selected.valueSeq().toArray().map(s => s.layerGeometry);
        const bounds = L.geoJSON(geoms).getBounds();
        this.getMap().then(map => {
            map.map.fitBounds(bounds);
        });
    }

    showSelected() {
        this.visible = this.visible.union(this.selected.keySeq().toArray());
        this.syncMapLayersToVisible()
    }

    hideSelected() {
        this.visible = this.visible.subtract(this.selected.keySeq().toArray());
        this.syncMapLayersToVisible();
    }

    deleteSelected() {
        this.deleteProjectAnalyses(this.selected.valueSeq().toArray());
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
    .component('rfProjectAnalysesPage', component).name;

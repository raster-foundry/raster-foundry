import tpl from './index.html';
import {colorStopsToRange, createRenderDefinition} from '_redux/histogram-utils';
import {nodesFromAst, astFromNodes} from '_redux/node-utils';
import _ from 'lodash';

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
        this.visible = new Set([]);
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
        this.visible.clear();
        this.visible.add(this.itemList[0].id);
        this.syncMapLayersToVisible();
    }

    showAllAnalyses() {
        this.itemList.forEach(item => {
            this.visible.add(item.id);
        });
        this.syncMapLayersToVisible();
    }

    fetchPage(page = this.$state.params.page || 1) {
        this.analysisList = [];
        this.itemList = [];
        this.itemActions = [];
        const currentQuery = this.analysisService.fetchAnalyses(
            {
                pageSize: 30,
                page: page - 1,
                projectId: this.project.id
            }
        ).then((paginatedAnalyses) => {
            this.analysisList = paginatedAnalyses.results;
            this.pagination = this.paginationService.buildPagination(paginatedAnalyses);
            this.paginationService.updatePageParam(page);
            if (this.currentQuery === currentQuery) {
                delete this.fetchError;
            }
            if (paginatedAnalyses.count) {
                this.itemList = this.analysisList.map(analysis => {
                    this.getAnalysisActions(analysis);
                    return this.createItemInfo(analysis);
                });
                if (parseInt(page, 10) === 1) {
                    this.visible.add(this.itemList[0].id);
                    this.syncMapLayersToVisible();
                }
            }
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
                const isEditAllowed = actions.includes('*') || actions.includes('EDIT');
                const isDeleteAllowed = actions.includes('*') || actions.includes('DELETE');
                if (isEditAllowed) {
                    this.itemActions.push(this.createItemActions(analysis, isDeleteAllowed));
                } else {
                    this.itemActions.push([]);
                }
            });
    }

    createItemInfo(analysis) {
        return {
            id: analysis.id,
            name: analysis.name,
            subtext: analysis.templateTitle,
            date: analysis.modifiedAt,
            colorGroupHex: analysis.layerColorGroupHex,
            geometry: analysis.layerGeometry
        };
    }

    createItemActions(analysis, isDeleteAllowed) {
        const editAction = {
            name: 'Edit',
            callback: () => {},
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

        let actions = [editAction];

        if (isDeleteAllowed) {
            actions.push(deleteAction);
        }

        return actions;
    }

    onHide(id) {
        if (this.visible.has(id)) {
            this.visible.delete(id);
        } else {
            this.visible.add(id);
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
        const visibleAnalysisIds = Array.from(this.visible);
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

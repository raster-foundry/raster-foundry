import { Map } from 'immutable';
import { get, min, max } from 'lodash';

import tpl from './index.html';
import {createRenderDefinition} from '_redux/histogram-utils';
import {nodesFromAst, astFromNodes} from '_redux/node-utils';

class AnalysesVisualizeController {
    constructor(
        $rootScope, $scope, $state, $log, $q,
        uuid4, mapService, projectService, analysisService
    ) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    $onInit() {
        this.isLoadingAnalysis = false;
        this.isLoadingAnalysisError = false;
        this.selected = new Map();
        this.analysesMap = new Map();
        this.projectId = this.$state.params.projectId;
        this.layerColorHex = {};
        this.analyses = this.getAnalysisIds().map(analysisId => {
            // create a trackId for each analysis to enable displaying
            // same analysis multiple times especially for map layers
            const trackId = this.uuid4.generate();
            const analysisPromise = this.mapLayerFromAnalysis(analysisId, trackId);
            return {
                id: analysisId,
                trackId,
                analysisTile: analysisPromise.then(a => a.analysisTile),
                histogram: analysisPromise.then(a => (
                    {histogram: a.histogram, statistics: a.statistics})),
                statistics: analysisPromise.then(a => a.statistics)
            };
        });

        this.histogramBounds = this.$q.all(this.analyses.map(a => a.statistics))
            .then(statistics => {
                return statistics.reduce((acc, stat) => {
                    return {
                        min: min([acc.min, stat.zmin]),
                        max: max([acc.max, stat.zmax])
                    };
                }, {});
            });
    }

    getAnalysisIds() {
        const analysis = this.$state.params.analysis;
        if (typeof this.$state.params.analysis === 'string') {
            return [analysis];
        }
        return analysis;
    }

    mapLayerFromAnalysis(analysisId, trackId) {
        this.isLoadingAnalysis = true;
        this.isLoadingAnalysisError = false;
        return this.$q.all({
            analysis: this.analysisService.getAnalysis(analysisId),
            histogram: this.analysisService.getNodeHistogram(analysisId),
            statistics: this.analysisService.getNodeStatistics(analysisId)
        }).then(({analysis, histogram, statistics}) => {
            this.analysesMap = this.analysesMap.set(trackId, analysis);
            this.setLayerColorHex(analysis);
            const {
                renderDefinition,
                histogramOptions
            } = createRenderDefinition(histogram);
            const newNodeDefinition = Object.assign(
                {}, analysis.executionParameters,
                {
                    metadata: Object.assign({}, analysis.executionParameters.metadata, {
                        renderDefinition,
                        histogramOptions
                    })
                }
            );
            const nodes = nodesFromAst(analysis.executionParameters);
            const updatedAnalysis = astFromNodes({analysis, nodes}, [newNodeDefinition]);
            this.analysisService.updateAnalysis(updatedAnalysis);
            return {analysis, histogram, statistics};
        }).then(({analysis, histogram, statistics}) => {
            const tileUrl = this.analysisService.getAnalysisTileUrl(analysisId);
            return {
                histogram,
                statistics,
                analysisTile: {
                    analysis,
                    mapTile: L.tileLayer(tileUrl, {maxZoom: 30})
                }
            };
        }).catch(err => {
            this.$log.error(err);
            this.isLoadingAnalysisError = true;
        }).finally(() => {
            this.isLoadingAnalysis = false;
        });
    }

    setLayerColorHex(analysis) {
        const projectLayerId = get(analysis, 'projectLayerId');
        if (projectLayerId) {
            this.projectService
                .getProjectLayer(this.projectId, projectLayerId)
                .then(layer => {
                    this.layerColorHex[analysis.id] = layer.colorGroupHex;
                })
                .catch(() => {});
        }
    }
}

const component = {
    bindings: {
        projectId: '<'
    },
    templateUrl: tpl,
    controller: AnalysesVisualizeController.name
};

export default angular
    .module('components.pages.project.analyses.visualize', [])
    .controller(AnalysesVisualizeController.name, AnalysesVisualizeController)
    .component('rfProjectAnalysesVisualizePage', component)
    .name;

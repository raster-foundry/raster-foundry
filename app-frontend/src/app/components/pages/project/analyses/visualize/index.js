import { Map } from 'immutable';
import { get } from 'lodash';

import tpl from './index.html';
import {createRenderDefinition} from '_redux/histogram-utils';
import {nodesFromAst, astFromNodes} from '_redux/node-utils';

class AnalysesVisualizeController {
    constructor(
        $rootScope, $scope, $state, $log,
        mapService, projectService, analysisService
    ) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    $onInit() {
        this.selected = new Map();
        this.projectId = this.$state.params.projectId;
        this.layerColorHex = {};
        this.analyses = this.getAnalysisIds().map(analysisId => {
            return {
                id: analysisId,
                analysisTile: this.mapLayerFromAnalysis(analysisId)
            };
        });
    }

    getAnalysisIds() {
        const analysis = this.$state.params.analysis;
        if (typeof this.$state.params.analysis === 'string') {
            return [analysis];
        }
        return analysis;
    }

    mapLayerFromAnalysis(analysisId) {
        return this.analysisService.getAnalysis(analysisId).then(analysis => {
            this.analysisService.getNodeHistogram(analysisId).then(histogram => {
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
                return this.analysisService.updateAnalysis(updatedAnalysis);
            });
            this.setLayerColorHex(analysis);
            return analysis;
        }).then((analysis) => {
            const tileUrl = this.analysisService.getAnalysisTileUrl(analysisId);
            return {
                analysis,
                mapTile: L.tileLayer(tileUrl, {maxZoom: 30})
            };
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

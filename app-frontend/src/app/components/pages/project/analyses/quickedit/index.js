import tpl from './index.html';
import { colorStopsToRange, createRenderDefinition } from '_redux/histogram-utils';
import { get, keys, min, max, range, first } from 'lodash';
import { Set, Map } from 'immutable';

class ProjectAnalysisQuickeditController {
    constructor($rootScope, $scope, $state, $q, analysisService, modalService) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    $onChanges(changes) {
        const analysis = get(changes, 'analysis.currentValue');
        if (analysis) {
            this.onAnalysisChanged(analysis);
        }
    }

    onAnalysisChanged(analysis) {
        this._analysis = analysis;
        this.loading = true;
        this.error = false;
        this.getHistogram(analysis)
            .then(histogram => {
                this.error = false;
                const numBuckets = get(histogram, 'buckets.length');
                const renderDefinition = get(
                    analysis,
                    'executionParameters.metadata.renderDefinition'
                );
                // re-fetch analysis since there's some syncing issues between this view
                // and the analyses list view that aren't really worth figuring out to remove
                // a single api call at this point
                if (numBuckets > 1 && !renderDefinition) {
                    return this.analysisService.getAnalysis(analysis.id).then(a => {
                        this._analysis = a;
                        return histogram;
                    });
                }
                return this.$q.resolve(histogram);
            })
            .then(histogram => {
                const numBuckets = get(histogram, 'buckets.length');
                if (numBuckets > 1) {
                    this.updateHistogramParameters(histogram);
                }
            })
            .catch(e => {
                this.error = e;
            })
            .finally(() => {
                this.loading = false;
            });
        this.editableNodeCount = this.countEditableNodesFromAnalysis(analysis);
    }

    countEditableNodesFromAnalysis(analysis) {
        const root = analysis.executionParameters;
        const nodeIds = {
            mask: new Set(),
            source: new Set(),
            const: new Set()
        };
        let nodes = [root];
        const addToCount = node =>
            (({
                mask: () => {
                    nodeIds.mask = nodeIds.mask.add(node.id);
                },
                layerSrc: () => {
                    nodeIds.source = nodeIds.source.add(node.id);
                },
                const: () => {
                    nodeIds.const = nodeIds.const.add(node.id);
                }
            }[node.type || node.apply] || (() => {}))());
        while (nodes.length) {
            const node = nodes.pop();
            addToCount(node);
            if (node.args) {
                nodes.push(...node.args);
            }
        }
        return {
            mask: nodeIds.mask.size,
            source: nodeIds.source.size,
            const: nodeIds.const.size
        };
    }

    getHistogram(analysis) {
        return this.analysisService.getNodeHistogram(analysis.id).then(histogram => {
            this.histogram = histogram;
            return histogram;
        });
    }

    updateHistogramParameters(histogram) {
        let { buckets } = histogram;
        this.metadata = get(this._analysis, 'executionParameters.metadata');
        const renderDefinition = get(this.metadata, 'renderDefinition');
        const calcRange = r => {
            r.range = r.max - r.min;
        };
        const dataRange = {
            min: histogram.minimum,
            max: histogram.maximum
        };
        calcRange(dataRange);
        let renderDefinitionKeys = keys(renderDefinition.breakpoints);
        const analysisRange = {
            min: min(renderDefinitionKeys.map(i => +i)),
            max: max(renderDefinitionKeys.map(i => +i))
        };
        calcRange(analysisRange);
        const fittingRange = {
            min: min([dataRange.min, analysisRange.min]),
            max: max([dataRange.max, analysisRange.max])
        };
        calcRange(fittingRange);

        const buffer = (fittingRange.max - fittingRange.min) * 0.1;
        const bufferedRange = {
            min: fittingRange.min - buffer,
            max: fittingRange.max + buffer
        };
        calcRange(bufferedRange);

        const diff = bufferedRange.range / 100;
        const magnitude = Math.round(Math.log10(diff));
        const precision = Math.pow(10, magnitude);

        this.ranges = {
            dataRange,
            analysisRange,
            fittingRange,
            buffer,
            bufferedRange,
            diff,
            magnitude,
            precision,
            noValidData: bufferedRange.range === 0
        };

        let valueMap = new Map();
        buckets.forEach(([bucket, value]) => {
            let roundedBucket = Math.round(bucket / precision) * precision;
            valueMap = valueMap.set(roundedBucket, value);
        });
        range(0, 100).forEach(mult => {
            let key = Math.round((mult * diff + bufferedRange.min) / precision) * precision;
            let value = valueMap.get(key, 0);
            valueMap = valueMap.set(key, value);
        });

        this.plot = valueMap
            .entrySeq()
            .sort(([K1], [K2]) => K1 - K2)
            .map(([key, value]) => ({ x: key, y: value }))
            .toArray();
    }

    onMetadataChange(metadata) {
        this._analysis = Object.assign({}, this._analysis, {
            executionParameters: Object.assign({}, this._analysis.executionParameters, {
                metadata
            })
        });
        this.updateHistogramParameters(this.histogram);
        this.analysisService.updateAnalysis(this._analysis).then(() => {
            this.onAnalysisUpdate(this._analysis);
        });
    }

    onEditClick() {
        this.modalService
            .open({
                component: 'rfAnalysisEditModal',
                resolve: {
                    analyses: () => [this._analysis]
                }
            })
            .result.then(updates => {
                this._analysis.executionParameters = first(updates).executionParameters;
                if (updates) {
                    delete this.histogram;
                    delete this.plot;
                    delete this.ranges;
                    this.onAnalysisUpdate(first(updates)).then(analysis => {
                        this.onAnalysisChanged(analysis);
                    });
                }
            })
            .catch(() => {});
    }
}

const component = {
    bindings: {
        analysis: '<',
        onAnalysisUpdate: '<'
    },
    templateUrl: tpl,
    controller: ProjectAnalysisQuickeditController.name
};

export default angular
    .module('components.pages.analyses.quickedit', [])
    .controller(ProjectAnalysisQuickeditController.name, ProjectAnalysisQuickeditController)
    .component('rfProjectAnalysisQuickeditPage', component).name;

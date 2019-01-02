import angular from 'angular';
import $ from 'jquery';
import _ from 'lodash';
import {Map} from 'immutable';
import nodeHistogramTpl from './nodeHistogram.html';
import LabActions from '_redux/actions/lab-actions';
import HistogramActions from '_redux/actions/histogram-actions';
import { getNodeDefinition, getNodeHistogram } from '_redux/node-utils';
import {
    breakpointsFromRenderDefinition, renderDefinitionFromState, colorStopsToRange
} from '_redux/histogram-utils';

const NodeHistogram = {
    templateUrl: nodeHistogramTpl,
    controller: 'NodeHistogramController',
    bindings: {
        nodeId: '<'
    }
};

class NodeHistogramController {
    constructor(
        $log, $rootScope, $scope, $element, $ngRedux,
        uuid4, histogramService, colorSchemeService,
        modalService, graphService
    ) {
        'ngInject';
        $rootScope.autoInject(this, arguments);

        this.defaultColorScheme = colorSchemeService.defaultColorSchemes.find(
            s => s.label === 'Viridis'
        );
    }

    mapStateToThis(state) {
        let node = getNodeDefinition(state, this);
        let nodeMetadata = node && node.metadata;
        let renderDefinition = nodeMetadata && nodeMetadata.renderDefinition;
        let histogramOptions = nodeMetadata && nodeMetadata.histogramOptions;
        let isSource = node && node.type && node.type.toLowerCase().includes('src');
        return {
            analysis: state.lab.analysis,
            analysisErrors: state.lab.analysisErrors,
            node,
            nodeMetadata,
            histogramOptions,
            renderDefinition,
            lastAnalysisRefresh: state.lab.lastAnalysisRefresh,
            histogram: getNodeHistogram(state, this),
            isSource
        };
    }

    $onInit() {
        let unsubscribe = this.$ngRedux.connect(
            this.mapStateToThis.bind(this),
            Object.assign({}, LabActions, HistogramActions)
        )(this);
        this.$scope.$on('$destroy', unsubscribe);

        this.graphId = this.uuid4.generate();
        this.getGraph = () => this.graphService.getGraph(this.graphId);

        this.debouncedBreakpointChange = _.debounce(this.onBreakpointChange.bind(this), 500);
        // once histogram and render definitions are initialized, plot data
        this.getGraph().then((graph) => {
            this.$scope.$watch('$ctrl.histogram', histogram => {
                if (histogram && histogram.data && this.renderDefinition) {
                    this.createPlotFromHistogram(histogram);
                    graph.setData({histogram: this.plot, breakpoints: this.breakpoints})
                        .setOptions(this.options)
                        .render();
                }
                if (!histogram && this.plot) {
                    delete this.plot;
                    graph.setData().render();
                }
            });
            const renderDefWatch = this.$scope.$watch('$ctrl.renderDefinition', (rdef) => {
                let changed = false;
                if ((!this.breakpoints || !this.breakpoints.length) && rdef) {
                    changed = true;
                    this.breakpoints = breakpointsFromRenderDefinition(
                        rdef, this.uuid4.generate
                    );
                    this.graph.setData({histogram: this.plot, breakpoints: this.breakpoints});
                }
                if (rdef) {
                    changed = true;
                    this.options = Object.assign({}, this.options, {
                        masks: {
                            min: rdef.clip === 'left' ||
                                rdef.clip === 'both',
                            max: rdef.clip === 'right' ||
                                rdef.clip === 'both'
                        },
                        scale: this.renderDefinition.scale,
                        breakpointRange: this.getBreakpointRange(this.breakpoints)
                    });
                    this.graph.setOptions(this.options);
                }
                if (changed) {
                    this.graph
                        .render();
                }
            });
        });
        // re-fetch histogram any time there's a hard update
        this.$scope.$watch('$ctrl.lastAnalysisRefresh', () => {
            this.fetchHistogram(this.nodeId);
        });
    }

    $postLink() {
        this.$scope.$evalAsync(() => this.initGraph());
    }

    $onDestroy() {
        if (this.graph) {
            this.graphService.deregister(this.graph.id);
        }
    }

    initGraph() {
        this.options = {
            dataRange: {
                min: 0,
                max: 255
            },
            breakpointRange: {
                min: 0,
                max: 255
            },
            masks: {
                min: false,
                max: false
            },
            scale: 'SEQUENTIAL'
        };

        if (this.histogramOptions) {
            this.options = Object.assign({}, this.options, {
                dataRange: this.histogramOptions.range,
                baseScheme: this.histogramOptions.baseScheme
            });
        }

        if (this.renderDefinition) {
            this.breakpoints = breakpointsFromRenderDefinition(
                this.renderDefinition, this.uuid4.generate
            );
            Object.assign({}, this.options, {
                masks: {
                    min: this.renderDefinition.clip === 'left' ||
                        this.renderDefinition.clip === 'both',
                    max: this.renderDefinition.clip === 'right' ||
                        this.renderDefinition.clip === 'both'
                },
                scale: this.renderDefinition.scale,
                breakpointRange: this.getBreakpointRange(this.breakpoints)
            });
        } else {
            this.breakpoints = Object.assign([]);
        }

        let elem = this.$element.find('.graph-container svg')[0];
        this.graph = this.graphService.register(elem, this.graphId, this.options);
    }

    roundToPrecision(val, precision) {
        return Math.round(val / precision) * precision;
    }

    /*
      Args:
      Histogram object with form {data, ...} (only data is relevant)
      @returns undefined
     */
    createPlotFromHistogram({data: histogram}) {
        let {min: bpmin, max: bpmax} = this.histogramOptions.range;
        this.options = Object.assign({}, this.options, {
            dataRange: {
                max: histogram.maximum,
                min: histogram.minimum
            },
            viewRange: {
                max: bpmax > histogram.maximum ? bpmax : histogram.maximum,
                min: bpmin < histogram.minimum ? bpmin : histogram.minimum
            }
        });

        let rangeSize = this.options.viewRange.max - this.options.viewRange.min;

        if (rangeSize === 0) {
            this.noValidData = true;
            return;
        } else if (this.noValidData) {
            this.noValidData = false;
        }

        let diff = rangeSize / 100;
        let magnitude = Math.round(Math.log10(diff));
        this.precision = Math.pow(10, magnitude);

        // recalibrate
        this.options.viewRange = {
            min: this.roundToPrecision(this.options.viewRange.min, this.precision),
            max: this.roundToPrecision(this.options.viewRange.max, this.precision)
        };

        diff = (this.options.viewRange.max - this.options.viewRange.min) / 100;

        let buckets = histogram.buckets;
        let plot;
        if (buckets.length < 100) {
            let valueMap = new Map();
            buckets.forEach(([bucket, value]) => {
                let roundedBucket = Math.round(bucket / this.precision) * this.precision;
                valueMap = valueMap.set(roundedBucket, value);
            });
            _.range(0, 100).forEach((mult) => {
                let key = Math.round(
                    (mult * diff + this.options.viewRange.min) / this.precision
                ) * this.precision;
                let value = valueMap.get(key, 0);
                valueMap = valueMap.set(key, value);
            });

            plot = valueMap.entrySeq()
                .sort(([K1], [K2]) => K1 - K2)
                .map(([key, value]) => ({x: key, y: value}))
                .toArray();
        } else {
            // TODO fix this so it acts like above
            plot = buckets.map((bucket) => {
                return {
                    x: Math.round(bucket[0]), y: bucket[1]
                };
            });
        }

        this.plot = plot;
    }

    rescaleInnerBreakpoints(breakpoint, value) {
        let minBreakpoint = this.breakpoints.reduce((min, current) => {
            if (!min.value) {
                return current;
            }
            return min.value < current.value ? min : current;
        }, {});
        let maxBreakpoint = this.breakpoints.reduce((max, current) => {
            if (!max.value) {
                return current;
            }
            return max.value > current.value ? max : current;
        }, {});
        let newRange = {
            min: minBreakpoint.value,
            max: maxBreakpoint.value
        };
        let oldRange = Object.assign({}, newRange);
        if (breakpoint.value > minBreakpoint.value) {
            newRange.max = value;
        } else if (breakpoint.value < maxBreakpoint.value) {
            newRange.min = value;
        }

        let absNewRange = newRange.max - newRange.min;
        let absOldRange = oldRange.max - oldRange.min;

        return this.breakpoints.map((bp) => {
            if (bp.id === breakpoint.id) {
                return Object.assign({}, bp, {value});
            } else if (bp.id === minBreakpoint.id || bp.id === maxBreakpoint.id) {
                return Object.assign({}, bp);
            }

            let percent = (bp.value - oldRange.min) / absOldRange;
            let newValue = percent * absNewRange + newRange.min;
            return Object.assign({}, bp, {value: newValue});
        });
    }

    onBreakpointChange(breakpoint, value) {
        if (breakpoint.options.style === 'bar') {
            this.breakpoints = this.rescaleInnerBreakpoints(breakpoint, value);
            this.options = Object.assign({}, this.options, {
                breakpointRange: this.getBreakpointRange(this.breakpoints)
            });
        } else {
            this.breakpoints = this.breakpoints.map((bp) => {
                if (bp.id === breakpoint.id) {
                    if (value > this.options.breakpointRange.max) {
                        return Object.assign({}, bp, {value: this.options.breakpointRange.max});
                    } else if (value < this.options.breakpointRange.min) {
                        return Object.assign({}, bp, {value: this.options.breakpointRange.min});
                    }
                    return Object.assign({}, bp, {value});
                }
                return Object.assign({}, bp);
            });
        }
        let {nodeId, breakpoints, options} = this;
        let renderDefinition = renderDefinitionFromState(options, breakpoints);
        let histogramOptions = Object.assign({}, this.histogramOptions, {
            range: this.options.breakpointRange
        });
        this.updateRenderDefinition({nodeId, renderDefinition, histogramOptions});
        if (this.graph) {
            this.graph
                .setData({histogram: this.plot, breakpoints: this.breakpoints})
                .setOptions(this.options)
                .render();
        }
        this.$scope.$evalAsync();
    }

    getBreakpointRange(breakpoints) {
        return breakpoints.reduce((range, current) => {
            let min;
            if (typeof range.min === 'undefined') {
                min = current.value;
            } else {
                min = range.min < current.value ? range.min : current.value;
            }
            let max;
            if (typeof range.min === 'undefined') {
                max = current.value;
            } else {
                max = range.max > current.value ? range.max : current.value;
            }
            return {
                min, max
            };
        }, {});
    }

    onMaskChange(maskChanged, value) {
        this.options = Object.assign({}, this.options, {
            masks: {
                min: maskChanged === 'min' ? value : this.options.masks.min,
                max: maskChanged === 'max' ? value : this.options.masks.max
            }
        });

        if (this.graph) {
            this.graph
                .setData({histogram: this.plot, breakpoints: this.breakpoints})
                .setOptions(this.options)
                .render();
        }

        let renderDefinition = renderDefinitionFromState(this.options, this.breakpoints);
        this.updateRenderDefinition({nodeId: this.nodeId, renderDefinition});
    }

    onColorSchemeChange(baseScheme) {
        let breakpointRange = this.getBreakpointRange(this.breakpoints);
        let breakpointMap = colorStopsToRange(
            baseScheme.colorScheme,
            breakpointRange.min,
            breakpointRange.max
        );
        this.breakpoints = breakpointsFromRenderDefinition(
            Object.assign({}, this.renderDefinition, {breakpoints: breakpointMap}),
            this.uuid4.generate
        );
        this.options = Object.assign({}, this.options, {
            baseScheme: Object.assign({}, baseScheme)
        });
        let renderDefinition = renderDefinitionFromState(this.options, this.breakpoints);

        if (this.graph) {
            this.graph
                .setData({histogram: this.plot, breakpoints: this.breakpoints})
                .setOptions(this.options)
                .render();
        }

        this.updateRenderDefinition({
            nodeId: this.nodeId,
            renderDefinition,
            histogramOptions: Object.assign({}, this.histogramOptions, {
                baseScheme
            })
        });
    }

    onBpMouseover({id}) {
        if (!this.isSource) {
            this.lastMouseOver = id;
        }
    }

    customColorModal() {
        const modal = this.modalService.open({
            component: 'rfColormapModal',
            // size: 'sm',
            resolve: {
                histogram: () => this.histogram,
                plot: () => this.plot,
                breakpoints: () => this.breakpoints.map(bp => ({value: bp.value, color: bp.color}))
            }
        });
        modal.result.then((bpMap) => {
            this.breakpoints = breakpointsFromRenderDefinition(
                Object.assign({}, this.renderDefinition, {breakpoints: bpMap}),
                this.uuid4.generate
            );

            const baseScheme = {
                dataType: 'CUSTOM',
                colorBins: 0,
                colorScheme: _.keys(bpMap)
                    .sort((a, b) => parseInt(a, 10) - parseInt(b, 10))
                    .map((value) => bpMap[value.toString()])
            };

            this.options = Object.assign({}, this.options, {
                baseScheme: Object.assign({}, baseScheme)
            });
            let renderDefinition = renderDefinitionFromState(this.options, this.breakpoints);

            if (this.graph) {
                this.graph
                    .setData({histogram: this.plot, breakpoints: this.breakpoints})
                    .setOptions(this.options)
                    .render();
            }

            this.updateRenderDefinition({
                nodeId: this.nodeId,
                renderDefinition,
                histogramOptions: Object.assign({}, this.histogramOptions, {
                    baseScheme
                })
            });
        }).catch(() => {});
    }
}

const NodeHistogramModule = angular.module('components.histogram.nodeHistogram', []);

NodeHistogramModule.component('rfNodeHistogram', NodeHistogram);
NodeHistogramModule.controller('NodeHistogramController', NodeHistogramController);

export default NodeHistogramModule;

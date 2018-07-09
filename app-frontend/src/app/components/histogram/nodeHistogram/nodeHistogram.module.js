import angular from 'angular';
import _ from 'lodash';
import d3 from 'd3';
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
        $log, $scope, $element, $ngRedux, uuid4,
        histogramService, colorSchemeService, modalService
    ) {
        'ngInject';
        this.$log = $log;
        this.$scope = $scope;
        this.$element = $element;
        this.histogramService = histogramService;
        this.uuid4 = uuid4;
        this.modalService = modalService;

        let unsubscribe = $ngRedux.connect(
            this.mapStateToThis.bind(this),
            Object.assign({}, LabActions, HistogramActions)
        )(this);
        $scope.$on('$destroy', unsubscribe);

        this.defaultColorScheme = colorSchemeService.defaultColorSchemes.find(
            s => s.label === 'Viridis'
        );

        const renderDefWatch = $scope.$watch('$ctrl.renderDefinition', (rdef) => {
            if (!this.breakpoints.length && rdef) {
                this.breakpoints = breakpointsFromRenderDefinition(
                    rdef, this.uuid4.generate
                );
                if (this.api.refresh) {
                    this.api.refresh();
                }
            } else if (rdef) {
                renderDefWatch();
            }
        });
    }

    mapStateToThis(state) {
        let node = getNodeDefinition(state, this);
        let nodeMetadata = node && node.metadata;
        let renderDefinition = nodeMetadata && nodeMetadata.renderDefinition;
        let histogramOptions = nodeMetadata && nodeMetadata.histogramOptions;
        let isSource = node.type && node.type.toLowerCase().includes('src');
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

        this.histOptions = {
            chart: {
                type: 'lineChart',
                showLegend: false,
                showXAxis: false,
                showYAxis: false,
                yScale: d3.scale.log(),
                margin: {
                    top: 0,
                    right: 0,
                    bottom: 0,
                    left: 0
                },
                height: 100,
                xAxis: {
                    showLabel: false
                },
                yAxis: {
                    showLabel: false
                },
                tooltip: {
                    enabled: false
                },
                interpolate: 'step',
                dispatch: {
                    renderEnd: () => {
                        this.updateHistogramColors();
                    }
                }
            }
        };

        let finishWaitingForGraphApi = this.$scope.$watch('$ctrl.api', (api) => {
            if (api.refresh) {
                this.refreshHistogram = _.throttle(this.api.refresh, 100);
                finishWaitingForGraphApi();
                this.refreshHistogram();
            }
        });

        this.api = {};

        this.debouncedBreakpointChange = _.debounce(this.onBreakpointChange.bind(this), 500);
        // once histogram and render definitions are initialized, plot data
        this.$scope.$watch('$ctrl.histogram', histogram => {
            if (histogram && histogram.data && this.renderDefinition) {
                this.createPlotFromHistogram(histogram);
            }
            if (!histogram && this.plot) {
                delete this.plot;
            }
        });
        // re-fetch histogram any time there's a hard update
        this.$scope.$watch('$ctrl.lastAnalysisRefresh', () => {
            this.fetchHistogram(this.nodeId);
        });
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

        this.plot = [{
            values: plot,
            key: 'Value',
            area: true}];
    }

    updateHistogramColors() {
        if (!this.api.getElement) {
            return;
        }

        let colors = this.calculateHistogramColors();

        this.updateHistogramGradient(colors);
    }

    calculateHistogramColors() {
        let range = this.options.viewRange.max - this.options.viewRange.min;
        let data = this.breakpoints.map((bp) => {
            let offset = (bp.value - this.options.viewRange.min) / range * 100;
            return {offset: offset, color: bp.color};
        }).sort((a, b) => a.offset - b.offset).map((bp) => {
            return {offset: `${bp.offset}%`, color: bp.color};
        });

        if (this.options.baseScheme && this.options.baseScheme.colorBins > 0) {
            let offsetData = data.map((currentValue, index, array) => {
                if (index !== array.length - 1) {
                    return {offset: array[index + 1].offset, color: currentValue.color};
                }
                return currentValue;
            });
            data = _.flatten(_.zip(data, offsetData));
        }

        if (this.options.masks.min || this.options.discrete) {
            let last = _.last(data);
            if (last.color === 'NODATA' || !this.options.discrete) {
                data.splice(0, 0, {offset: data[0].offset, color: '#353C58'});
                data.splice(0, 0, {offset: data[0].offset, color: '#353C58'});
            } else {
                data.splice(0, 0, {offset: data[0].offset, color: _.first(data.color)});
                data.splice(0, 0, {offset: data[0].offset, color: last.color});
            }
        }
        if (this.options.masks.max || this.options.discrete) {
            let last = _.last(data);
            if (last.color === 'NODATA' || !this.options.discrete) {
                data.push({offset: _.last(data).offset, color: '#353C58'});
                data.push({offset: _.last(data).offset, color: '#353C58'});
            } else {
                data.push({offset: _.last(data).offset, color: last.color});
            }
        }
        return data;
    }

    updateHistogramGradient(data) {
        let svg = d3.select(this.api.getElement().children()[0]);
        let defs = svg.select('defs')[0].length ? svg.select('defs') : svg.append('defs');
        let linearGradient = defs.selectAll('linearGradient')[0].length ?
            defs.selectAll('linearGradient') : defs.append('linearGradient');

        linearGradient.attr('id', `line-gradient-${this.nodeId}`)
            .attr('gradientUnits', 'userSpaceOnUse')
            .attr('x1', '0%').attr('y1', 0)
            .attr('x2', '100%').attr('y2', 0)
            .selectAll('stop')
            .data(data)
            .enter().append('stop')
            .attr('offset', (d) => d.offset)
            .attr('stop-color', (d) => d.color)
            .attr('stop-opacity', (d) => Number.isFinite(d.opacity) ? d.opacity : 1.0);
        this.$scope.$evalAsync();
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
        this.api.refresh();
        let {nodeId, breakpoints, options} = this;
        let renderDefinition = renderDefinitionFromState(options, breakpoints);
        let histogramOptions = Object.assign({}, this.histogramOptions, {
            range: this.options.breakpointRange
        });
        this.updateRenderDefinition({nodeId, renderDefinition, histogramOptions});
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
        this.api.refresh();

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

        this.api.refresh();

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

            this.api.refresh();

            this.updateRenderDefinition({
                nodeId: this.nodeId,
                renderDefinition,
                histogramOptions: Object.assign({}, this.histogramOptions, {
                    baseScheme
                })
            });
        });
    }
}

const NodeHistogramModule = angular.module('components.histogram.nodeHistogram', []);

NodeHistogramModule.component('rfNodeHistogram', NodeHistogram);
NodeHistogramModule.controller('NodeHistogramController', NodeHistogramController);

export default NodeHistogramModule;

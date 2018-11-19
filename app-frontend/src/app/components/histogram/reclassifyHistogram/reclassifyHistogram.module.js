import * as d3 from 'd3';
import _ from 'lodash';
import angular from 'angular';
import reclassifyHistogramTpl from './reclassifyHistogram.html';

const ReclassifyHistogramComponent = {
    templateUrl: reclassifyHistogramTpl,
    controller: 'ReclassifyHistogramController',
    bindings: {
        histogram: '<',
        classifications: '<',
        options: '<',
        onMasksChange: '&',
        onBreakpointChange: '&'
    }
};

class ReclassifyHistogramController {
    constructor($rootScope, $log, $scope, $element, uuid4, graphService) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    $onInit() {
        this.graphId = this.uuid4.generate();
        this.getGraph = () => this.graphService.getGraph(this.graphId);

        this.getGraph().then((graph) => {
            this.$scope.$watch('$ctrl.histogram', histogram => {
                if (histogram) {
                    this.usingDefaultData = false;
                    const plot = this.createPlotFromHistogram(histogram);
                    graph.setData({histogram: plot, breakpoints: this._breakpoints})
                        .setOptions(this._options)
                        .render();
                } else if (!histogram) {
                    graph.setData().render();
                }
            });
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

    $onChanges(changes) {
        if (changes.classifications && changes.classifications.currentValue) {
            this._breakpoints = changes.classifications.currentValue.map(bp => {
                if (bp.id) {
                    return bp;
                }
                bp.id = this.uuid4.generate();
                bp.options = {
                    style: 'bar',
                    alwaysShowNumbers: false
                };
                return bp;
            });
        }
    }

    initGraph() {
        this._options = {
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

        if (!Number.isFinite(this.precision)) {
            this.precision = 1;
        }

        this.histogramRange = {
            min: this._options.dataRange.min,
            max: this._options.dataRange.max
        };

        let elem = this.$element.find('.graph-container svg')[0];
        this.graph = this.graphService.register(elem, this.graphId, this._options);
    }

    setDefaults(options) {
        this._options = Object.assign({
            min: 0,
            max: 255,
            masks: {
                min: false,
                max: false
            },
            scale: 'SEQUENTIAL'
        }, options ? options : {});
    }

    createPlotFromHistogram(data) {
        let bufferConstant = 0.1;
        let newHistogram = data;
        let breakpoints = this.classifications.map(b => b.break);
        let min = Math.min(newHistogram.minimum, ...breakpoints, 0);
        min = min - min * bufferConstant;
        let max = Math.max(newHistogram.maximum, ...breakpoints);
        max = max + max * bufferConstant;
        let range = max - min;
        let diff = range / 500;
        let magnitude = Math.round(Math.log10(diff));
        this.precision = Math.pow(10, magnitude);
        if (range) {
            this.histogramRange = { min, max };
        }

        let buckets = newHistogram.buckets;
        let plot;
        if (buckets && buckets.length < 100) {
            let valMap = {};
            _.range(0, 100).map((mult) => {
                let key = mult * diff + newHistogram.minimum;
                let roundedkey = Math.round(key / this.precision) * this.precision;
                valMap[roundedkey] = 0;
            });
            buckets.forEach((bucket) => {
                let key = bucket[0];
                let roundedkey = Math.round(key / this.precision) * this.precision;
                valMap[roundedkey] = bucket[1];
            });
            plot = Object.keys(valMap)
                .map(key => parseFloat(key))
                .sort((a, b) => a - b)
                .map((key) => {
                    return {
                        x: key, y: valMap[key]
                    };
                });
        } else if (buckets) {
            plot = buckets.map((bucket) => {
                return {
                    x: Math.round(bucket[0]), y: bucket[1]
                };
            });
        }

        if (plot) {
            if (_.first(plot).x > min) {
                plot.splice(0, 0, {x: min, y: 0});
            }
            if (_.last(plot).x < max) {
                plot.push({x: max, y: 0});
            }
        }
        return plot;
    }

    onChange(breakpoint, index) {
        this._breakpoints[index].break = breakpoint;
        this.sendBreakpoints(this._breakpoints);
    }

    sendBreakpoints(breakpoints) {
        this.onBreakpointChange({
            breakpoints,
            options: this._options
        });
    }

    getLowerBoundForIndex(index) {
        return index === 0 ?
            Math.min(this.histogramRange.min, this.histogram.minimum) :
            this._breakpoints[index - 1].break;
    }

    getUpperBoundForIndex(index) {
        return index === this._breakpoints.length - 1 ?
            Math.max(this.histogramRange.max, this.histogram.maximum) :
            this._breakpoints[index + 1].break;
    }
}

const ReclassifyHistogramModule = angular.module('components.histogram.reclassifyHistogram', []);

ReclassifyHistogramModule.component('rfReclassifyHistogram', ReclassifyHistogramComponent);
ReclassifyHistogramModule.controller(
    'ReclassifyHistogramController', ReclassifyHistogramController
);

export default ReclassifyHistogramModule;

/* global d3 _ */
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
    constructor($log, $scope, $element, uuid4) {
        'ngInject';
        this.$log = $log;
        this.$scope = $scope;
        this.$element = $element;
        this.uuid4 = uuid4;
    }

    $onInit() {
        if (!this._options) {
            this.setDefaults();
        }

        let cancelApiWatch = this.$scope.$watch('$ctrl.api', (api) => {
            if (api.refresh) {
                this.refreshHistogram = _.throttle(this.api.refresh, 100);
                cancelApiWatch();
                this.refreshHistogram();
            }
        });
        this.id = this.uuid4.generate();
        this.api = {};

        if (!Number.isFinite(this.precision)) {
            this.precision = 1;
        }
        if (!this.histogramRange) {
            this.histogramRange = {min: this._options.min, max: this._options.max};
        }
    }

    $onChanges(changes) {
        if (changes.options) {
            this.setDefaults(changes.options.currentValue);
        }

        if (changes.histogram && changes.histogram.currentValue) {
            this.processDataToPlot(changes.histogram.currentValue);
            this.usingDefaultData = false;
        }

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
                interpolate: 'step'
            }
        };
    }

    processDataToPlot(data) {
        let bufferConstant = 1.1;
        let newHistogram = data;
        let breakpoints = this.classifications.map(b => b.break);
        let min = Math.min(newHistogram.minimum, ...breakpoints, 0) * bufferConstant;
        let max = Math.max(newHistogram.maximum, ...breakpoints) * bufferConstant;
        let range = max - min;
        let diff = range / 100;
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
            this.plot = [{
                values: plot,
                key: 'Value',
                area: true}];
        }
    }

    onChange(breakpoint, index) {
        this._breakpoints[index].break = breakpoint;
        this.sendBreakpoints(this._breakpoints);
        this.onBreakpointChange({
            breakpoints: this._breakpoints, options: this._options
        });
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

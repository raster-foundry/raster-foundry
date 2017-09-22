/* global d3 _ */

export default class ReclassifyHistogramController {
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

        this.onBreakpointChange({
            breakpoints: this._breakpoints, options: this._options
        });

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
            let breakpoints = Object.keys(changes.classifications.currentValue).map(b => {
                return { value: +b };
            });
            this._breakpoints = breakpoints.map(bp => {
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
        let newHistogram = data;
        let range = newHistogram.maximum - newHistogram.minimum;
        let diff = range / 100;
        let magnitude = Math.round(Math.log10(diff));
        this.precision = Math.pow(10, magnitude);
        if (range) {
            this.rescaleBreakpoints(newHistogram.minimum, newHistogram.maximum);
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
            if (_.first(plot).x !== newHistogram.minimum) {
                plot.splice(0, 0, {x: newHistogram.minimum, y: 0});
            }
            if (_.last(plot).x !== newHistogram.maximum) {
                plot.push({x: newHistogram.maximum, y: 0});
            }
            this.plot = [{
                values: plot,
                key: 'Value',
                area: true}];
        }
    }

    rescaleBreakpoints(min, max) {
        let currentRange = this.histogramRange ?
            this.histogramRange.max - this.histogramRange.min :
            this._options.max - this._options.min;
        let newRange = max - min;
        if (this._breakpoints && currentRange !== newRange && newRange > 0) {
            if (this._options) {
                this.onBreakpointChange({
                    breakpoints: this._breakpoints, options: this._options
                });
            }
        }
        this.histogramRange = {min: min, max: max};
    }

    onChange(bp, breakpoint) {
        bp.value = breakpoint;
        this.onBreakpointChange({
            breakpoints: this._breakpoints, options: this._options
        });
    }

    recalculateBreakpointsFromRange(min, max) {
        let oldmin = _.first(this._breakpoints).value;
        let oldmax = _.last(this._breakpoints).value;

        let currentRange = oldmax - oldmin;
        let newRange = max - min;
        if (this._breakpoints && currentRange !== newRange && newRange > 0) {
            this._breakpoints.forEach((bp) => {
                let percent = (
                    bp.value - oldmin
                ) / currentRange;
                let newVal = percent * newRange + min;
                bp.value = newVal;
            });
        }
    }

    getLowerBoundForIndex(index) {
        return index === 0 ?
            Math.min(0, this.histogram.minimum) :
            this._breakpoints[index - 1].value;
    }

    getUpperBoundForIndex(index) {
        return index === this._breakpoints.length - 1 ?
            this.histogram.maximum :
            this._breakpoints[index + 1].value;
    }
}

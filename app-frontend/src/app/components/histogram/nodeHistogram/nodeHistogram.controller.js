/* globals d3 _ */
/* eslint-disable*/
const uuid = function b(a){return a?(a^Math.random()*16>>a/4).toString(16):([1e7]+-1e3+-4e3+-8e3+-1e11).replace(/[018]/g,b)};
/* eslint-enable */

const defaultData = {minimum: 0, maximum: 255, buckets: _.range(0, 256).map((x) => {
    let y = x === 0 || x === 255 ? 0 : 1;
    return [x, y];
})};

export default class NodeHistogramController {
    constructor($log, $scope, $element) {
        'ngInject';
        this.$log = $log;
        this.$scope = $scope;
        this.$element = $element;
    }

    onMaskChange() {
        if (this.refreshHistogram) {
            this.refreshHistogram();
        }
        this.onBreakpointChange({breakpoints: this._breakpoints, masks: this._masks});
    }

    $onInit() {
        this.setDefaults(this.options);
        this.$scope.$watch('$ctrl.options.discrete', () => {
            if (this.refreshHistogram) {
                this.refreshHistogram();
            }
            this.onBreakpointChange({breakpoints: this._breakpoints, masks: this._masks});
        });
        let cancelApiWatch = this.$scope.$watch('$ctrl.api', (api) => {
            if (api.refresh) {
                this.refreshHistogram = _.throttle(this.api.refresh, 100);
                cancelApiWatch();
                this.refreshHistogram();
            }
        });
        this.id = uuid();
        this.api = {};
        this.onBreakpointChange({breakpoints: this._breakpoints, masks: this._masks});
        if (!Number.isFinite(this.precision)) {
            this.precision = 0;
        }
        if (!this.histogramRange) {
            this.histogramRange = {min: this.options.min, max: this.options.max};
        }
    }

    $onChanges(changes) {
        if (changes.options && changes.options.currentValue) {
            this.setDefaults(changes.options.currentValue);
        }

        if (changes.histogram && changes.histogram.currentValue) {
            this.processDataToPlot(changes.histogram.currentValue);
            this.usingDefaultData = false;
        } else if (changes.histogram && !changes.histogram.currentValue) {
            this.processDataToPlot(defaultData);
            this.usingDefaultData = true;
        }

        if (changes.masks && changes.masks.currentValue) {
            this._masks = Object.assign({min: false, max: false}, changes.masks.currentValue);
            this.minChecked = this._masks.min;
            this.maxChecked = this._masks.max;
        }

        if (changes.breakpoints && changes.breakpoints.currentValue) {
            this._breakpoints = changes.breakpoints.currentValue;
        }
    }

    updateHistogramColors() {
        if (!this.api.getElement) {
            return;
        }

        let svg = d3.select(this.api.getElement().children()[0]);
        let defs = svg.select('defs')[0].length ? svg.select('defs') : svg.append('defs');
        let linearGradient = defs.selectAll('linearGradient')[0].length ?
            defs.selectAll('linearGradient') : defs.append('linearGradient');
        let range = 255;
        if (this.histogramRange) {
            range = this.histogramRange.max - this.histogramRange.min;
        } else {
            range = this.options.max - this.options.min;
        }
        let data;
        if (this.histogramRange) {
            data = this._breakpoints.map((bp) => {
                let offset = (bp.breakpoint.value - this.histogramRange.min) / range * 100;
                return {offset: `${offset}%`, color: bp.breakpoint.color};
            });
        } else {
            data = this._breakpoints.map((bp) => {
                let offset = (bp.breakpoint.value - this.options.min) / range * 100;
                return {offset: `${offset}%`, color: bp.breakpoint.color};
            });
        }
        if (this.options.discrete) {
            let offsetData = data.map((currentValue, index, array) => {
                if (index !== array.length - 1) {
                    return {offset: array[index + 1].offset, color: currentValue.color};
                }
                return currentValue;
            });
            data = _.flatten(_.zip(data, offsetData));
        }
        if (this._masks.min || this.options.discrete) {
            let last = _.last(data);
            if (last.color === 'NODATA' || !this.options.discrete) {
                data.splice(0, 0, {offset: data[0].offset, color: '#353C58'});
                data.splice(0, 0, {offset: data[0].offset, color: '#353C58'});
            } else {
                data.splice(0, 0, {offset: data[0].offset, color: _.first(data.color)});
                data.splice(0, 0, {offset: data[0].offset, color: last.color});
            }
        }
        if (this._masks.max || this.options.discrete) {
            let last = _.last(data);
            if (last.color === 'NODATA' || !this.options.discrete) {
                data.push({offset: _.last(data).offset, color: '#353C58'});
                data.push({offset: _.last(data).offset, color: '#353C58'});
            } else {
                data.push({offset: _.last(data).offset, color: last.color});
            }
        }
        linearGradient.attr('id', `line-gradient-${this.id}`)
            .attr('gradientUnits', 'userSpaceOnUse')
            .attr('x1', '0%').attr('y1', 0)
            .attr('x2', '100%').attr('y2', 0)
            .selectAll('stop')
            .data(data)
            .enter().append('stop')
            .attr('offset', (d) => d.offset)
            .attr('stop-color', (d) => d.color)
            .attr('stop-opacity', (d) => Number.isFinite(d.opacity) ? d.opacity : 1.0);
    }

    setDefaults(options) {
        this.options = Object.assign({
            min: 0,
            max: 255
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
                interpolate: 'step',
                dispatch: {
                    renderEnd: () => {
                        this.updateHistogramColors();
                    }
                }
            }
        };

        if (!this._breakpoints) {
            this._breakpoints = [
                {
                    key: 'min',
                    breakpoint: {
                        value: this.options.min,
                        style: 'bar',
                        alwaysShowNumbers: true,
                        color: '#ff0000'
                    }
                }, {
                    key: 'max',
                    breakpoint: {
                        value: this.options.max,
                        style: 'bar',
                        alwaysShowNumbers: true,
                        color: '#ff0000'
                    }
                }
            ];
        }
    }

    rescaleBreakpoints(min, max) {
        let currentRange = this.histogramRange ?
            this.histogramRange.max - this.histogramRange.min :
            this.options.max - this.options.min;
        let newRange = max - min;
        if (this._breakpoints && currentRange !== newRange && newRange > 0) {
            this._breakpoints.forEach((bp) => {
                let breakpoint = bp.breakpoint;
                let percent = (
                    breakpoint.value -
                        (this.histogramRange ? this.histogramRange.min : this.options.min)
                ) / currentRange;
                let newVal = percent * newRange + min;
                breakpoint.value = newVal;
                return breakpoint;
            });
            if (this._masks) {
                this.onBreakpointChange({breakpoints: this._breakpoints, masks: this._masks});
            }
        }
        this.histogramRange = {min: min, max: max};
    }

    processDataToPlot(data) {
        let newHistogram = data;
        let range = newHistogram.maximum - newHistogram.minimum;
        if (range === 0) {
            this.noValidData = true;
            newHistogram = defaultData;
            range = newHistogram.maximum - newHistogram.minimum;
        } else if (this.noValidData) {
            this.noValidData = false;
        }
        let diff = range / 100;
        let magnitude = Math.round(Math.log10(diff));
        this.precision = Math.pow(10, magnitude);

        this.rescaleBreakpoints(newHistogram.minimum, newHistogram.maximum);

        let buckets = newHistogram.buckets;
        let plot;
        if (buckets.length < 100) {
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
        } else {
            plot = buckets.map((bucket) => {
                return {
                    x: Math.round(bucket[0]), y: bucket[1]
                };
            });
        }

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

    onChange(bp, breakpoint) {
        let min = _.first(this._breakpoints).breakpoint.value;
        let max = _.last(this._breakpoints).breakpoint.value;
        let index = this._breakpoints.findIndex((b) => b.breakpoint === bp);
        if (index === 0) {
            let second = this._breakpoints[1].breakpoint.value;
            bp.value = breakpoint < second ? breakpoint : second;
        } else if (index === this._breakpoints.length - 1) {
            let secondToLast = this._breakpoints[this._breakpoints.length - 2].breakpoint.value;
            bp.value = breakpoint > secondToLast ? breakpoint : secondToLast;
        } else {
            bp.value = Math.min(Math.max(breakpoint, min), max);
            this._breakpoints.sort((a, b) => a.breakpoint.value - b.breakpoint.value);
        }

        this.onBreakpointChange({breakpoints: this._breakpoints, masks: this._masks});
        if (this.refreshHistogram) {
            this.refreshHistogram();
        }
    }
}

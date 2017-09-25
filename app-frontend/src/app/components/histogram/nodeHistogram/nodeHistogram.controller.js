/* globals d3 _ */
/* eslint-disable*/
const uuid = function b(a){return a?(a^Math.random()*16>>a/4).toString(16):([1e7]+-1e3+-4e3+-8e3+-1e11).replace(/[018]/g,b)};
/* eslint-enable */

const defaultHistogramData = {minimum: 0, maximum: 255, buckets: _.range(0, 256).map((x) => {
    let y = x === 0 || x === 255 ? 0 : 1;
    return [x, y];
})};
export default class NodeHistogramController {
    constructor($log, $scope, $element, histogramService) {
        'ngInject';
        this.$log = $log;
        this.$scope = $scope;
        this.$element = $element;
        this.histogramService = histogramService;
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
        this.id = uuid();
        this.api = {};

        this.onBreakpointChange({
            breakpoints: this._breakpoints, options: this._options
        });

        if (!Number.isFinite(this.precision)) {
            this.precision = 0;
        }
    }

    $onChanges(changes) {
        if (changes.options) {
            this.setDefaults(changes.options.currentValue);
            this.baseColorScheme = this._options && this._options.baseScheme;
        }

        if (changes.histogram && changes.histogram.currentValue) {
            this.processDataToPlot(changes.histogram.currentValue);
            this.usingDefaultData = false;
        } else if (changes.histogram && !changes.histogram.currentValue) {
            this.processDataToPlot(defaultHistogramData);
            this.usingDefaultData = true;
        }

        if (changes.breakpoints && changes.breakpoints.currentValue) {
            this._breakpoints = changes.breakpoints.currentValue.map((bp, index, arr) => {
                if (bp.id) {
                    return bp;
                }
                let isEndpoint = index === 0 || index === arr.length - 1;
                bp.id = uuid();
                bp.options = {
                    style: isEndpoint ? 'bar' : 'arrow',
                    alwaysShowNumbers: isEndpoint
                };

                return bp;
            });
            this._baseScheme = {
                colorScheme: this._breakpoints.map((bp) => bp.color),
                bins: 0,
                dataType: 'SEQUENTIAL'
            };
        }
    }

    onMaskChange() {
        if (this.refreshHistogram) {
            this.refreshHistogram();
        }
        this.onBreakpointChange({
            breakpoints: this._breakpoints, options: this._options
        });
    }

    updateHistogramColors() {
        if (!this.api.getElement) {
            return;
        }

        let svg = d3.select(this.api.getElement().children()[0]);
        let defs = svg.select('defs')[0].length ? svg.select('defs') : svg.append('defs');
        let linearGradient = defs.selectAll('linearGradient')[0].length ?
            defs.selectAll('linearGradient') : defs.append('linearGradient');
        let range = this._options.range.max - this._options.range.min;
        let data = this._breakpoints.map((bp) => {
            let offset = (bp.value - this._options.range.min) / range * 100;
            return {offset: `${offset}%`, color: bp.color};
        });

        if (this._options.baseScheme && this._options.baseScheme.colorBins > 0) {
            let offsetData = data.map((currentValue, index, array) => {
                if (index !== array.length - 1) {
                    return {offset: array[index + 1].offset, color: currentValue.color};
                }
                return currentValue;
            });
            data = _.flatten(_.zip(data, offsetData));
        }

        if (this._options.masks.min || this._options.discrete) {
            let last = _.last(data);
            if (last.color === 'NODATA' || !this._options.discrete) {
                data.splice(0, 0, {offset: data[0].offset, color: '#353C58'});
                data.splice(0, 0, {offset: data[0].offset, color: '#353C58'});
            } else {
                data.splice(0, 0, {offset: data[0].offset, color: _.first(data.color)});
                data.splice(0, 0, {offset: data[0].offset, color: last.color});
            }
        }
        if (this._options.masks.max || this._options.discrete) {
            let last = _.last(data);
            if (last.color === 'NODATA' || !this._options.discrete) {
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
                interpolate: 'step',
                dispatch: {
                    renderEnd: () => {
                        this.updateHistogramColors();
                    }
                }
            }
        };
    }

    rescaleBreakpoints(min, max) {
        let currentRange = this._options.range.max - this._options.range.min;
        let newRange = max - min;


        if (this._breakpoints && currentRange !== newRange && newRange > 0) {
            this.histogramService.scaleBreakpointsToRange(
                this._breakpoints,
                {
                    min: this._options.range.min,
                    max: this._options.range.max
                }, {
                    min: min, max: max
                }
            );
            if (this._options) {
                Object.assign(this._options.range, {
                    min: min, max: max
                });
                this.onBreakpointChange({
                    breakpoints: this._breakpoints, options: this._options
                });
            }
        }
    }

    processDataToPlot(data) {
        let newHistogram = data;
        let range = newHistogram.maximum - newHistogram.minimum;
        if (range === 0) {
            this.noValidData = true;
            newHistogram = defaultHistogramData;
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
        let min = _.first(this._breakpoints).value;
        let max = _.last(this._breakpoints).value;

        let index = this._breakpoints.findIndex((b) => b === bp);

        if (!this._options.baseScheme || this._options.baseScheme.colorBins === 0) {
            if (index === 0) {
                this.recalculateBreakpointsFromRange(breakpoint, max);
            } else if (index === this._breakpoints.length - 1) {
                this.recalculateBreakpointsFromRange(min, breakpoint);
            } else {
                this.$log.error(
                    'Tried to change non-endpoint breakpoint for non-categorical color map'
                );
            }
        } else if (index === 0) {
            let second = this._breakpoints[1].value;
            bp.value = breakpoint < second ? breakpoint : second;
        } else if (index === this._breakpoints.length - 1) {
            let secondToLast = this._breakpoints[this._breakpoints.length - 2].value;
            bp.value = breakpoint > secondToLast ? breakpoint : secondToLast;
        } else {
            bp.value = Math.min(Math.max(breakpoint, min), max);
            this._breakpoints.sort((a, b) => a.value - b.value);
        }

        this.onBreakpointChange({
            breakpoints: this._breakpoints, options: this._options
        });
        if (this.refreshHistogram) {
            this.refreshHistogram();
        }
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

    shouldShowBreakpoint(index) {
        if (!this._options.baseScheme ||
            this._options.baseScheme.colorBins === 0
           ) {
            return index === 0 || index === this._breakpoints.length - 1;
        }
        return true;
    }

    getColorBins(colors, bins) {
        const step = (colors.length - 1) / (bins - 1);
        return _.range(0, bins).map((i) => colors[Math.round(step * i)]);
    }

    onColorSchemeChange(colorSchemeOptions) {
        if (this._options.baseScheme &&
            colorSchemeOptions.colorBins === this._options.baseScheme.colorBins &&
            JSON.stringify(colorSchemeOptions.colorScheme) ===
            JSON.stringify(this._options.baseScheme.colorScheme)
           ) {
            return;
        }
        let min = this._options.range.min;
        let max = this._options.range.max;

        const binned = colorSchemeOptions.colorBins > 0;

        let numColors = binned ?
            colorSchemeOptions.colorBins : colorSchemeOptions.colorScheme.length;

        this.rescaleBreakpoints(0, numColors - 1);
        let colors = binned ?
            this.getColorBins(colorSchemeOptions.colorScheme, colorSchemeOptions.colorBins) :
            colorSchemeOptions.colorScheme;
        this._breakpoints = colors.map((color, index, arr) => {
            let isEndpoint = index === 0 || index === arr.length - 1;
            return {
                id: uuid(),
                value: index,
                color: color,
                options: {
                    style: isEndpoint ? 'bar' : 'arrow',
                    alwaysShowNumbers: isEndpoint
                }
            };
        });
        this._options.scale = colorSchemeOptions.dataType;
        this._options.baseScheme = colorSchemeOptions;
        this.rescaleBreakpoints(min, max);
    }
}

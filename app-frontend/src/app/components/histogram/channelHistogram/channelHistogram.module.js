/* globals _ d3 */
import angular from 'angular';

import channelHistogramTpl from './channelHistogram.html';

const ChannelHistogram = {
    templateUrl: channelHistogramTpl,
    controller: 'ChannelHistogramController',
    bindings: {
        data: '<',
        corrections: '<',
        onChange: '&'
    }
};

class ChannelHistogramController {
    constructor($log, $scope) {
        'ngInject';
        this.$log = $log;
        this.$scope = $scope;
    }

    $onInit() {
        this.histogramMode = 'rgb';
        this.minClip = 0;
        this.maxClip = 255;
        this.lowerClip = this.minClip;
        this.upperClip = this.maxClip;
        this.clipping = {
            rgb: {
                min: this.minClip, max: this.maxClip,
                color: 'white'
            },
            red: {
                min: this.minClip, max: this.maxClip,
                color: 'red'
            },
            green: {
                min: this.minClip, max: this.maxClip,
                color: 'green'
            },
            blue: {
                min: this.minClip, max: this.maxClip,
                color: 'blue'
            }
        };

        this.histOptions = {
            chart: {
                type: 'lineChart',
                showLegend: false,
                showXAxis: false,
                showYAxis: false,
                yScale: d3.scale.log(),
                deepWatchData: true,
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
                }
            }
        };

        if (this.data && this.data[0] && this.data[1] && this.data[2]) {
            this.plots = this.bandsToPlots(this.data[0], this.data[1], this.data[2]);
        }

        const debouncedUpdate = _.debounce(this.updateClipping.bind(this), 500);

        this.$scope.$watch('$ctrl.lowerClip', debouncedUpdate);
        this.$scope.$watch('$ctrl.upperClip', debouncedUpdate);
    }

    updateClipping() {
        if (this.histogramMode && this.lastHistogramModeUpdate === this.histogramMode) {
            let clipping = this.clipping[this.histogramMode];
            let changed = false;
            if (clipping.max !== this.upperClip) {
                if (this.histogramMode === 'rgb') {
                    _.forOwn(this.clipping, (clip) => {
                        clip.max = this.upperClip;
                    });
                } else {
                    clipping.max = this.upperClip;
                }
                changed = true;
            }
            if (clipping.min !== this.lowerClip) {
                if (this.histogramMode === 'rgb') {
                    _.forOwn(this.clipping, (clip) => {
                        clip.min = this.lowerClip;
                    });
                } else {
                    clipping.min = this.lowerClip;
                }
                changed = true;
            }

            if (changed) {
                this.onChange({clipping: this.clipping});
            }
        }
        this.lastHistogramModeUpdate = this.histogramMode;
    }

    $onChanges(changesObj) {
        if ('data' in changesObj && changesObj.data.currentValue) {
            let data = changesObj.data.currentValue;
            if (data[0] && data[1] && data[2]) {
                this.plots = this.bandsToPlots(data[0], data[1], data[2]);
                this.setHistogramMode();
            }
        }

        if ('corrections' in changesObj && changesObj.corrections.currentValue) {
            let corr = changesObj.corrections.currentValue;
            let tileClipping = {
                min:
                Number.isInteger(corr.tileClipping.min) ? corr.tileClipping.min : this.minClip,
                max:
                Number.isInteger(corr.tileClipping.max) ? corr.tileClipping.max : this.maxClip
            };

            Object.assign(
                this.clipping.rgb,
                {min: tileClipping.min, max: tileClipping.max}
            );
            Object.assign(
                this.clipping.red,
                {
                    min: Number.isInteger(corr.bandClipping.redMin) ?
                        corr.bandClipping.redMin : tileClipping.min,
                    max: Number.isInteger(corr.bandClipping.redMax) ?
                        corr.bandClipping.redMax : tileClipping.max
                }
            );
            Object.assign(
                this.clipping.green,
                {
                    min: Number.isInteger(corr.bandClipping.greenMin) ?
                        corr.bandClipping.greenMin : tileClipping.min,
                    max: Number.isInteger(corr.bandClipping.greenMax) ?
                        corr.bandClipping.greenMax : tileClipping.max
                }
            );
            Object.assign(
                this.clipping.blue,
                {
                    min: Number.isInteger(corr.bandClipping.blueMin) ?
                        corr.bandClipping.blueMin : tileClipping.min,
                    max: Number.isInteger(corr.bandClipping.blueMax) ?
                        corr.bandClipping.blueMax : tileClipping.max
                }
            );
        }
    }

    bandsToPlots(redBand, greenBand, blueBand) {
        let rgbSum = {};
        let maxY = 0;

        let histogramRange = _.range(0, 256);

        let redPlot = histogramRange.map((redXKey) => {
            let redX = parseInt(redXKey, 10);
            let redY = parseInt(redBand[redXKey.toString()] ? redBand[redXKey] : 0, 10);
            rgbSum[redXKey] = redY;
            return {x: redX, y: redY};
        });
        let greenPlot = histogramRange.map((greenXKey) => {
            let greenX = parseInt(greenXKey, 10);
            let greenY = parseInt(greenBand[greenXKey.toString()] ? greenBand[greenXKey] : 0, 10);
            rgbSum[greenXKey] =
                rgbSum[greenXKey] ?
                rgbSum[greenXKey] + greenY :
                greenY;
            return {x: greenX, y: greenY};
        });
        let bluePlot = histogramRange.map((blueXKey) => {
            let blueX = parseInt(blueXKey, 10);
            let blueY = parseInt(blueBand[blueXKey.toString()] ? blueBand[blueXKey] : 0, 10);
            rgbSum[blueXKey] =
                rgbSum[blueXKey] ?
                rgbSum[blueXKey] + blueY :
                blueY;
            return {x: blueX, y: blueY};
        });

        let rgbPlot = Object.keys(rgbSum).map((aggXKey) => {
            let aggX = parseInt(aggXKey, 10);
            let aggY = parseInt(rgbSum[aggXKey], 10);
            maxY = aggY > maxY ? aggY : maxY;
            return {x: aggX, y: aggY};
        });

        return {
            red: redPlot,
            green: greenPlot,
            blue: bluePlot,
            rgb: rgbPlot,
            maxY: maxY
        };
    }

    setHistogramMode(mode) {
        if (mode) {
            this.updateClipping();
            this.histogramMode = mode;
        }


        if (this.histogramMode === 'rgb') {
            this.histData = [{
                values: this.plots.rgb,
                key: 'rgb',
                color: '#959cad',
                area: true
            }];
            this.lowerClip = this.clipping.rgb.min;
            this.upperClip = this.clipping.rgb.max;
        } else if (this.histogramMode === 'red') {
            this.histData = [{
                values: this.plots.red,
                key: 'red',
                color: '#ed1841',
                area: true
            }];
            this.lowerClip = this.clipping.red.min;
            this.upperClip = this.clipping.red.max;
        } else if (this.histogramMode === 'green') {
            this.histData = [{
                values: this.plots.green,
                key: 'green',
                color: '#28af5f',
                area: true
            }];
            this.lowerClip = this.clipping.green.min;
            this.upperClip = this.clipping.green.max;
        } else if (this.histogramMode === 'blue') {
            this.histData = [{
                values: this.plots.blue,
                key: 'blue',
                color: '#206fed',
                area: true
            }];
            this.lowerClip = this.clipping.blue.min;
            this.upperClip = this.clipping.blue.max;
        }
    }

    onMinBreakpointChange(breakpoint) {
        this.lowerClip = breakpoint;
        if (this.lowerClip > this.upperClip) {
            this.upperClip = this.lowerClip;
        }
        this.$scope.$evalAsync();
    }

    onMaxBreakpointChange(breakpoint) {
        this.upperClip = breakpoint;
        if (this.upperClip < this.lowerClip) {
            this.lowerClip = this.upperClip;
        }
        this.$scope.$evalAsync();
    }
}

const ChannelHistogramModule = angular.module('components.histogram.channelHistogram', []);

ChannelHistogramModule.component('rfChannelHistogram', ChannelHistogram);
ChannelHistogramModule.controller('ChannelHistogramController', ChannelHistogramController);

export default ChannelHistogramModule;

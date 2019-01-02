import _ from 'lodash';
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

const minClip = 0;
const maxClip = 255;

class ChannelHistogramController {
    constructor($log, $rootScope, $scope, $element, graphService, uuid4) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    $onInit() {
        this.graphid = this.uuid4.generate();
        this.getGraph = () => this.graphService.getGraph(this.graphId);
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
        this.histogramMode = 'rgb';
        this.lowerClip = minClip;
        this.upperClip = maxClip;

        this.options = {
            clipping: {
                rgb: {
                    min: minClip, max: maxClip,
                    color: 'white'
                },
                red: {
                    min: minClip, max: maxClip,
                    color: 'red'
                },
                green: {
                    min: minClip, max: maxClip,
                    color: 'green'
                },
                blue: {
                    min: minClip, max: maxClip,
                    color: 'blue'
                }
            },
            type: 'channel'
        };

        if (this.data && this.data[0] && this.data[1] && this.data[2]) {
            this.plots = this.bandsToPlots(this.data[0], this.data[1], this.data[2]);
        }

        let elem = this.$element.find('.graph-container svg')[0];
        this.graph = this.graphService.register(elem, this.graphId, this.options);
        this.graph.setData(this.plots);

        const debouncedUpdate = _.debounce(this.updateClipping.bind(this), 500);
        this.$scope.$watch('$ctrl.lowerClip', debouncedUpdate);
        this.$scope.$watch('$ctrl.upperClip', debouncedUpdate);
    }

    updateClipping() {
        let clipping = this.options.clipping[this.histogramMode];
        let changed = false;
        if (clipping.max !== this.upperClip) {
            if (this.histogramMode === 'rgb') {
                _.forOwn(this.options.clipping, (clip) => {
                    clip.max = this.upperClip;
                });
            } else {
                clipping.max = this.upperClip;
            }
            changed = true;
        }
        if (clipping.min !== this.lowerClip) {
            if (this.histogramMode === 'rgb') {
                _.forOwn(this.options.clipping, (clip) => {
                    clip.min = this.lowerClip;
                });
            } else {
                clipping.min = this.lowerClip;
            }
            changed = true;
        }

        if (changed) {
            this.onChange({clipping: this.options.clipping});
        }
        this.lastHistogramModeUpdate = this.histogramMode;
        this.graph.setOptions(this.options);
        if (this.plots) {
            this.graph.setData(this.plots).render();
        }
    }

    $onChanges(changesObj) {
        if ('data' in changesObj && changesObj.data.currentValue) {
            let data = changesObj.data.currentValue;
            if (data[0] && data[1] && data[2]) {
                this.plots = this.bandsToPlots(data[0], data[1], data[2]);
                this.setHistogramMode();
                if (this.plots && this.options) {
                    this.graph.setData(this.plots).render();
                }
            }
        }

        if ('corrections' in changesObj && changesObj.corrections.currentValue) {
            let corr = changesObj.corrections.currentValue;
            let tileClipping = {
                min:
                Number.isInteger(corr.tileClipping.min) ? corr.tileClipping.min : minClip,
                max:
                Number.isInteger(corr.tileClipping.max) ? corr.tileClipping.max : maxClip
            };

            Object.assign(
                this.options.clipping.rgb,
                {min: tileClipping.min, max: tileClipping.max}
            );
            Object.assign(
                this.options.clipping.red,
                {
                    min: Number.isInteger(corr.bandClipping.redMin) ?
                        corr.bandClipping.redMin : tileClipping.min,
                    max: Number.isInteger(corr.bandClipping.redMax) ?
                        corr.bandClipping.redMax : tileClipping.max
                }
            );
            Object.assign(
                this.options.clipping.green,
                {
                    min: Number.isInteger(corr.bandClipping.greenMin) ?
                        corr.bandClipping.greenMin : tileClipping.min,
                    max: Number.isInteger(corr.bandClipping.greenMax) ?
                        corr.bandClipping.greenMax : tileClipping.max
                }
            );
            Object.assign(
                this.options.clipping.blue,
                {
                    min: Number.isInteger(corr.bandClipping.blueMin) ?
                        corr.bandClipping.blueMin : tileClipping.min,
                    max: Number.isInteger(corr.bandClipping.blueMax) ?
                        corr.bandClipping.blueMax : tileClipping.max
                }
            );
            if (this.graph && this.plots) {
                this.graph.setData(this.plots).setOptions(this.options).render();
            }
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
            this.histogramMode = mode;
        }

        this.options.channel = this.histogramMode;
        this.lowerClip = this.options.clipping[this.histogramMode].min;
        this.upperClip = this.options.clipping[this.histogramMode].max;

        this.updateClipping();
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

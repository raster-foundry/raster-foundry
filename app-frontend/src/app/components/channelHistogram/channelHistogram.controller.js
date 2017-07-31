/* globals _ d3 */
export default class ChannelHistogramController {
    constructor($log, $scope) {
        'ngInject';
        this.$log = $log;
        this.$scope = $scope;
    }

    $onInit() {
        this.histogramMode = 'rgb';
        this.minClip = 0;
        this.maxClip = 255;
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
                useInteractiveGuideline: true,
                interactive: true,
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
                },
                lines: {
                    dispatch: {
                        elementClick: (event) => {
                            this.onClick({value: this.hoverValue, event: event});
                        }
                    }
                },
                interactiveLayer: {
                    tooltip: {
                        contentGenerator: (data) => {
                            this.hoverValue = data.value;
                            return `<p>Value: ${data.value}</p>
                                    <p>Pixels: ${data.series[0].value}</p>`;
                        }
                    }
                }
            }
        };

        if (this.data && this.data[0] && this.data[1] && this.data[2]) {
            this.plots = this.bandsToPlots(this.data[0], this.data[1], this.data[2]);
        }
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
            this.histogramMode = mode;
        }
        if (this.histogramMode === 'rgb') {
            this.histData = [{
                values: this.plots.rgb,
                key: 'rgb',
                color: 'grey',
                area: true
            }];
        } else if (this.histogramMode === 'red') {
            this.histData = [{
                values: this.plots.red,
                key: 'red',
                color: 'red',
                area: true
            }];
        } else if (this.histogramMode === 'green') {
            this.histData = [{
                values: this.plots.green,
                key: 'green',
                color: 'green',
                area: true
            }];
        } else if (this.histogramMode === 'blue') {
            this.histData = [{
                values: this.plots.blue,
                key: 'blue',
                color: 'blue',
                area: true
            }];
        }

        this.applyMinMaxBars();
    }

    setMinMaxBars(min, max) {
        if (this.histogramMode === 'rgb') {
            Object.keys(this.clipping).map((mode) => {
                if (Number.isInteger(min)) {
                    this.clipping[mode].min = min;
                }
                if (Number.isInteger(max)) {
                    this.clipping[mode].max = max;
                }
            });
        } else {
            if (Number.isInteger(min)) {
                this.clipping[this.histogramMode].min = min;
            }
            if (Number.isInteger(max)) {
                this.clipping[this.histogramMode].max = max;
            }
        }

        this.applyMinMaxBars();
        this.onChange({clipping: this.clipping});
    }

    applyMinMaxBars() {
        let shouldShowModeClipping = (mode) => {
            let isSameAsRgb = this.clipping[mode].min === this.clipping.rgb.min &&
                this.clipping[mode].max === this.clipping.rgb.max;
            return mode === 'rgb' ||
                !isSameAsRgb && (mode === this.histogramMode || this.histogramMode === 'rgb');
        };

        this.currentModeMin = this.clipping[this.histogramMode].min;
        this.currentModeMax = this.clipping[this.histogramMode].max;
        this.histData = this.histData.filter((plot) => {
            return !plot.key.contains('min') && !plot.key.contains('max');
        });

        Object.keys(this.clipping).map((mode) => {
            let modeSettings = this.clipping[mode];

            if (shouldShowModeClipping(mode)) {
                let showArea = this.histogramMode === mode;
                let minvalues = [
                    {x: modeSettings.min, y: this.plots.maxY},
                    {x: modeSettings.min, y: 0}
                ];
                let maxvalues = [
                    {x: modeSettings.max, y: 0},
                    {x: modeSettings.max, y: this.plots.maxY}
                ];

                if (showArea) {
                    minvalues.unshift({x: 0, y: this.plots.maxY});
                    maxvalues.push({x: 255, y: this.plots.maxY});
                }

                this.histData.push({
                    key: `${mode}min${showArea ? '+area' : ''}`,
                    values: minvalues,
                    color: modeSettings.color,
                    fillOpacity: 0.1,
                    area: showArea
                });
                this.histData.push({
                    key: `${mode}max${showArea ? '+area' : ''}`,
                    values: maxvalues,
                    color: modeSettings.color,
                    fillOpacity: 0.1,
                    area: showArea
                });
            }
        });
        this.histData = [].concat(this.histData);
    }

    onClick(event) {
        let x = event.value;
        let modeSettings = this.clipping[this.histogramMode];
        let min = modeSettings.min;
        let max = modeSettings.max;
        let minDiff = x - min;
        let maxDiff = max - x;
        if (minDiff < maxDiff) {
            this.setMinMaxBars(x, max);
        } else {
            this.setMinMaxBars(min, x);
        }
        this.$scope.$evalAsync();
    }

    onClipInputChange(currentModeMin, currentModeMax) {
        if (Number.isInteger(currentModeMin)) {
            this.currentModeMin = currentModeMin >= this.minClip ? currentModeMin : this.minClip;
        }
        if (Number.isInteger(currentModeMax)) {
            this.currentModeMax = currentModeMax <= this.maxClip ? currentModeMax : this.maxClip;
        }
        this.setMinMaxBars(this.currentModeMin, this.currentModeMax);
    }
}

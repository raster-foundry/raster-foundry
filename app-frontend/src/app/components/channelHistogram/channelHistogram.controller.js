export default class ChannelHistogramController {
    constructor() {
        'ngInject';
    }

    $onInit() {
        this.histOptions = {
            chart: {
                type: 'lineChart',
                showLegend: false,
                showXAxis: false,
                showYAxis: false,
                margin: {
                    top: 0,
                    right: 0,
                    bottom: 0,
                    left: 0
                },
                xAxis: {
                    showLabel: false
                },
                yAxis: {
                    showLabel: false
                }
            }
        };

        if (this.data) {
            this.histData = Array.from(this.data);
        }
    }

    $onChanges(changesObj) {
        if ('data' in changesObj && 'currentValue' in changesObj.data) {
            this.histData = Array.from(changesObj.data.currentValue);
        }
    }
}

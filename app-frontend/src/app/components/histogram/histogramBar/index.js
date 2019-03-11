import { min, max, head, last } from 'lodash';

import tpl from './index.html';

class HistogramBarController {
    constructor(
        $rootScope, $scope, $q, $element, $log,
    ) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    $onInit() {
        this.bars = [];
        this.$q.all({
            statistics: this.statistics,
            bounds: this.bounds,
            histStats: this.histogram
        }).then(({statistics, bounds, histStats}) => {
            const { histogram } = histStats;
            this.bars = [
                {
                    value: this.valueToPercentage(bounds, statistics.mean),
                    color: 'blue'
                },
                {
                    value: this.valueToPercentage(bounds, statistics.median),
                    color: 'green'
                },
                {
                    value: this.valueToPercentage(bounds, statistics.mode),
                    color: 'cyan'
                },
                {
                    value: this.valueToPercentage(bounds, statistics.mean - statistics.stddev),
                    color: 'purple'
                },
                {
                    value: this.valueToPercentage(bounds, statistics.mean + statistics.stddev),
                    color: 'purple'
                },
                {
                    value: this.valueToPercentage(bounds, statistics.zmin),
                    color: 'black'
                },
                {
                    value: this.valueToPercentage(bounds, statistics.zmax),
                    color: 'black'
                }
            ];
        });
    }

    valueToPercentage(bounds, val) {
        return (val - bounds.min) / (bounds.max - bounds.min) * 100;
    }
}

const component = {
    bindings: {
        statistics: '<',
        bounds: '<',
        histogram: '<'
    },
    templateUrl: tpl,
    controller: HistogramBarController.name
};

export default angular
    .module('components.histogram.histogramBar', [])
    .controller(HistogramBarController.name, HistogramBarController)
    .component('rfHistogramBar', component)
    .name;

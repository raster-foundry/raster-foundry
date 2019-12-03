import tpl from './index.html';

class HistogramBarController {
    constructor($rootScope, $scope, $q, $element, $log) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    $onInit() {
        this.bars = [];
        this.$q
            .all({
                statistics: this.statistics,
                bounds: this.boundsPromise,
                histStats: this.histogramPromise
            })
            .then(({ statistics, bounds, histStats }) => {
                const { histogram } = histStats;
                this.bars = [
                    {
                        value: this.valueToPercentage(bounds, statistics.mean),
                        class: 'bg-gray-lighter'
                    },
                    {
                        value: this.valueToPercentage(bounds, statistics.median),
                        class: 'bg-green '
                    },
                    {
                        value: this.valueToPercentage(bounds, statistics.mode),
                        class: 'bg-tertiary'
                    },
                    {
                        value: this.valueToPercentage(bounds, statistics.mean - statistics.stddev),
                        class: 'bg-danger'
                    },
                    {
                        value: this.valueToPercentage(bounds, statistics.mean + statistics.stddev),
                        class: 'bg-danger'
                    },
                    {
                        value: this.valueToPercentage(bounds, statistics.zmin),
                        class: 'bg-black'
                    },
                    {
                        value: this.valueToPercentage(bounds, statistics.zmax),
                        class: 'bg-black'
                    }
                ];
            });
    }

    valueToPercentage(bounds, val) {
        return ((val - bounds.min) / (bounds.max - bounds.min)) * 100;
    }

    getStyle(val) {
        return { left: val + '%' };
    }
}

const component = {
    bindings: {
        statistics: '<',
        boundsPromise: '<',
        histogramPromise: '<'
    },
    templateUrl: tpl,
    controller: HistogramBarController.name
};

export default angular
    .module('components.histogram.histogramBar', [])
    .controller(HistogramBarController.name, HistogramBarController)
    .component('rfHistogramBar', component).name;

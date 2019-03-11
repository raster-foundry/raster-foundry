import _ from 'lodash';
import tpl from './index.html';
import { Map } from 'immutable';

class DataVizHistogramController {
    constructor(
        $rootScope, $scope, $log, $q, $element,
        graphService
    ) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    $postLink() {
        this.$q.all({
            histStat: this.histogram,
            bounds: this.bounds
        }).then(({histStat, bounds}) => {
            const {histogram, statistics} = histStat;
            this.$scope.$evalAsync(() => this.initGraph(histogram, bounds));
            this.getGraph().then(graph => {
                this.createPlotFromHistogram(histogram, bounds, statistics);
                graph.setData({histogram: this.plot})
                    .render();
            });
        });
    }

    initGraph(histogram, bounds) {
        this.options = {
            dataRange: {
                min: bounds.min,
                max: bounds.max
            },
            height: 50,
            style: {
                height: '50px'
            },
            type: 'stat',
            margin: {
                top: 0,
                bottom: 0,
                right: 0,
                left: 0
            }
        };

        const elem = this.$element.find('.graph-container svg')[0];
        this.graph = this.graphService.register(elem, this.id, this.options);
    }

    getGraph() {
        return this.graphService.getGraph(this.id);
    }

    createPlotFromHistogram(histogram, bounds, statistics) {
        let diff = (bounds.max - bounds.min) / 100;
        let magnitude = Math.round(Math.log10(diff));
        this.precision = Math.pow(10, magnitude);

        let buckets = histogram.buckets;
        let plot;
        if (buckets.length < 100) {
            let valueMap = new Map();
            buckets.forEach(([bucket, value]) => {
                let roundedBucket = Math.round(bucket / this.precision) * this.precision;
                valueMap = valueMap.set(roundedBucket, value);
            });
            _.range(0, 100).forEach((mult) => {
                let key = Math.round(
                    (mult * diff + this.options.dataRange.min) / this.precision
                ) * this.precision;
                let value = valueMap.get(key, 0);
                valueMap = valueMap.set(key, value);
            });

            plot = valueMap.entrySeq()
                .sort(([K1], [K2]) => K1 - K2)
                .map(([key, value]) => ({
                    x: _.min([_.max([key, histogram.minimum]), histogram.maximum]),
                    y: value}))
                .toArray();
        } else {
            // TODO fix this so it acts like above
            plot = buckets.map((bucket) => {
                return {
                    x: Math.round(bucket[0]), y: bucket[1]
                };
            });
        }

        this.plot = plot;
    }
}

const component = {
    bindings: {
        id: '<',
        histogram: '<',
        bounds: '<'
    },
    templateUrl: tpl,
    controller: DataVizHistogramController.name,
    transclude: true
};

export default angular
    .module('components.histogram.dataVizHistogram', [])
    .controller(DataVizHistogramController.name, DataVizHistogramController)
    .component('rfDataVizHistogram', component)
    .name;

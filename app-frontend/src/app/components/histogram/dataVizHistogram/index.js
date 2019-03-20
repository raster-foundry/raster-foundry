import { min, max, range, get } from 'lodash';
import tpl from './index.html';
import { Map } from 'immutable';

class DataVizHistogramController {
    constructor(
        $rootScope, $scope, $log, $q, $element, $window,
        graphService
    ) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    $postLink() {
        this.isLoadingHistogram = true;
        this.$q.all({
            histStat: this.histogramPromise,
            histBounds: this.boundsPromise
        }).then(({histStat, histBounds}) => {
            const { histogram } = histStat;
            this.histogramData = histogram;
            this.histogramBounds = histBounds;
            if (this.histogramData &&
                this.histogramBounds &&
                get(this.histogramData, 'buckets.length')
            ) {
                this.$scope.$evalAsync(() => {
                    this.initGraphOptions(this.histogramBounds);
                    this.visualizeHistogram();
                });
            }
        });

        this.$window.addEventListener('resize', this.onWindowResize.bind(this));
    }

    $onDestroy() {
        if (this.onWindowResize) {
            this.$window.removeEventListener('resize', this.onWindowResize);
        }
        this.graphService.deregister(this.id);
    }

    onWindowResize() {
        this.getGraph().then(graph => {
            graph.render();
        });
    }

    visualizeHistogram() {
        this.initGraph();
        this.getGraph().then(graph => {
            const plot =
                this.createPlotFromHistogram(this.histogramData, this.histogramBounds);
            graph.setData({ histogram: plot }).render();
            this.isLoadingHistogram = false;
        });
    }

    initGraphOptions(bounds) {
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
    }

    initGraph() {
        const elem = this.$element.find('.graph-container svg')[0];
        this.graph = this.graphService.register(elem, this.id, this.options);
    }

    getGraph() {
        return this.graphService.getGraph(this.id);
    }

    createPlotFromHistogram(histogram, bounds) {
        const diff = (bounds.max - bounds.min) / 100;
        const magnitude = Math.round(Math.log10(diff));
        this.precision = Math.pow(10, magnitude);

        const buckets = histogram.buckets;
        let plot;
        if (buckets.length < 100) {
            let valueMap = new Map();
            buckets.forEach(([bucket, value]) => {
                const roundedBucket = Math.round(bucket / this.precision) * this.precision;
                valueMap = valueMap.set(roundedBucket, value);
            });
            range(0, 100).forEach((mult) => {
                const key = Math.round(
                    (mult * diff + this.options.dataRange.min) / this.precision
                ) * this.precision;
                const value = valueMap.get(key, 0);
                valueMap = valueMap.set(key, value);
            });

            plot = valueMap.entrySeq()
                .sort(([K1], [K2]) => K1 - K2)
                .map(([key, value]) => ({
                    x: min([max([key, histogram.minimum]), histogram.maximum]),
                    y: value}))
                .toArray();
        } else {
            plot = buckets.map((bucket) => {
                return {
                    x: Math.round(bucket[0]), y: bucket[1]
                };
            });
        }

        return plot;
    }
}

const component = {
    bindings: {
        id: '<',
        histogramPromise: '<',
        boundsPromise: '<'
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

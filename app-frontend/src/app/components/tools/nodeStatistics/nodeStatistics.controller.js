/* global _ */

const visibleStats = ['mean', 'median', 'mode', 'stddev', 'zmin', 'zmax'];

export default class NodeHistogramController {
    constructor($scope, $filter, toolService) {
        'ngInject';
        this.$scope = $scope;
        this.$filter = $filter;
        this.toolService = toolService;
    }

    $onInit() {
        this.initSize();
        this.isLoading = false;
        this.hasStats = false;
        this.digitCount = 5;
        this.emptyStats = { };
        visibleStats.forEach((stat) => {
            this.emptyStats[stat] = '';
        });
    }

    initSize() {
        let size = this.size;
        this.model.set('size', {width: size.width, height: 260});
    }

    $onChanges() {
        this.fetchStats();
    }

    fetchStats() {
        if (
            this.model &&
            this.toolrun &&
            this.toolrun.id
        ) {
            const nodeId = this.model.get('id');
            const runId = this.toolrun.id;

            this.hasToolRun = true;
            this.isLoading = true;

            this.toolService.getNodeStatistics(runId, nodeId).then(stats => {
                // Scrub the response of angular remnants to tell if it's actually empty
                this.stats = Object.keys(stats)
                    .filter((key) => visibleStats.indexOf(key) > -1)
                    .reduce((acc, s) => {
                        if (!s.startsWith('$')) {
                            acc[s] = stats[s];
                        }
                        return acc;
                    }, {});

                this.hasStats = !_.isEmpty(this.stats);
                this.isLoading = false;
            });
        } else {
            this.hasToolRun = false;
            this.isLoading = false;
            this.hasStats = false;
        }
    }

    shouldShowStats() {
        return !this.isLoading && this.hasStats;
    }

    shouldShowEmptyStats() {
        return !this.isLoading && !this.hasStats;
    }

    shouldShowApplyMessage() {
        return !this.hasToolRun && !this.isLoading && !this.hasStats;
    }

    shouldShowDataMessage() {
        return this.hasToolRun && !this.isLoading && !this.hasStats;
    }

    parseStatValDisplay(val) {
        let result = this.$filter('number')(val, 5);
        if (result && result.length && result !== '-∞' && result !== '∞') {
            result = result.split(',').join('');
        }
        return result;
    }
}

/* global _ */

export default class NodeHistogramController {
    constructor($scope, toolService) {
        'ngInject';
        this.$scope = $scope;
        this.toolService = toolService;
    }

    $onInit() {
        this.initSize();
        this.isLoading = false;
        this.hasStats = false;
        this.digitCount = 5;
        this.emptyStats = {
            dataCells: '',
            mean: '',
            median: '',
            mode: '',
            stddev: '',
            zmin: '',
            zmax: ''
        };
    }

    initSize() {
        let size = this.size;
        this.model.set('size', {width: size.width, height: 290});
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
                this.stats = Object.keys(stats).reduce((acc, s) => {
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

    shouldShowEmptyStats() {
        return !this.isLoading && !this.hasStats;
    }

    shouldShowApplyMessage() {
        return !this.hasToolRun && !this.isLoading && !this.hasStats;
    }

    shouldShowDataMessage() {
        return this.hasToolRun && !this.isLoading && !this.hasStats;
    }

}

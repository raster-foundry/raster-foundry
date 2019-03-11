import tpl from './index.html';

class DataVizStatisticsController {
    constructor(
        $rootScope, $scope, $log, $q
    ) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    $onInit() {
        this.statistics.then(stat => {
            this.stat = stat;
        });
    }
}

const component = {
    bindings: {
        statistics: '<'
    },
    templateUrl: tpl,
    controller: DataVizStatisticsController.name
};

export default angular
    .module('components.histogram.dataVizStatistics', [])
    .controller(DataVizStatisticsController.name, DataVizStatisticsController)
    .component('rfDataVizStatistics', component)
    .name;

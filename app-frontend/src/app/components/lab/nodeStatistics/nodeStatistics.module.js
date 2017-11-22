import angular from 'angular';

import StatisticsActions from '_redux/actions/statistics-actions';

import nodeStatisticsTpl from './nodeStatistics.html';

const NodeStatisticsComponent = {
    templateUrl: nodeStatisticsTpl,
    controller: 'NodeStatisticsController',
    bindings: {
        nodeId: '<'
    }
};


class NodeStatisticsController {
    constructor($scope, $ngRedux, $filter) {
        'ngInject';
        this.$scope = $scope;
        this.$filter = $filter;

        this.visibleStats = ['mean', 'median', 'mode', 'stddev', 'zmin', 'zmax'];

        let unsubscribe = $ngRedux.connect(
            this.mapStateToThis.bind(this),
            StatisticsActions
        )(this);
        $scope.$on('$destroy', unsubscribe);

        // re-fetch statistics every time there's a hard update
        this.$scope.$watch('$ctrl.lastAnalysisRefresh', () => {
            this.fetchStatistics(this.nodeId);
        });
    }

    mapStateToThis(state) {
        return {
            lastAnalysisRefresh: state.lab.lastAnalysisRefresh,
            statistics: state.lab.statistics.get(this.nodeId)
        };
    }

    $onInit() {
        this.digitCount = 5;
        this.emptyStats = { };
        this.visibleStats.forEach((stat) => {
            this.emptyStats[stat] = '';
        });
    }

    parseStatValDisplay(val) {
        let result = this.$filter('number')(val, 5);
        if (result && result.length && result !== '-∞' && result !== '∞') {
            result = result.split(',').join('');
        }
        return result;
    }
}
const NodeStatisticsModule = angular.module('components.lab.nodeStatistics', []);

NodeStatisticsModule.component('rfNodeStatistics', NodeStatisticsComponent);
NodeStatisticsModule.controller('NodeStatisticsController', NodeStatisticsController);

export default NodeStatisticsModule;

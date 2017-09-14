import nodeStatisticsTpl from './nodeStatistics.html';

const nodeStatistics = {
    templateUrl: nodeStatisticsTpl,
    controller: 'NodeStatisticsController',
    bindings: {
        model: '<',
        toolrun: '<',
        size: '<'
    }
};

export default nodeStatistics;

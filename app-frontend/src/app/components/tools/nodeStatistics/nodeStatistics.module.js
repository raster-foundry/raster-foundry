import angular from 'angular';

import NodeStatistics from './nodeStatistics.component.js';
import NodeStatisticsController from './nodeStatistics.controller.js';

const NodeStatisticsModule = angular.module('components.statistics.nodeStatistics', []);

NodeStatisticsModule.component('rfNodeStatistics', NodeStatistics);
NodeStatisticsModule.controller('NodeStatisticsController', NodeStatisticsController);

export default NodeStatisticsModule;

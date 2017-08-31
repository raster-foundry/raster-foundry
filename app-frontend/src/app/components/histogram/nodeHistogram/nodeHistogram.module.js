import angular from 'angular';

import NodeHistogram from './nodeHistogram.component.js';
import NodeHistogramController from './nodeHistogram.controller.js';

const NodeHistogramModule = angular.module('components.histogram.nodeHistogram', []);

NodeHistogramModule.component('rfNodeHistogram', NodeHistogram);
NodeHistogramModule.controller('NodeHistogramController', NodeHistogramController);

export default NodeHistogramModule;

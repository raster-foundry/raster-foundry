/* eslint-disable */
import angular from 'angular';

import ReclassifyHistogram from './reclassifyHistogram.component.js';
import ReclassifyHistogramController from './reclassifyHistogram.controller.js';

const ReclassifyHistogramModule = angular.module('components.histogram.reclassifyHistogram', []);

ReclassifyHistogramModule.component('rfReclassifyHistogram', ReclassifyHistogram);
ReclassifyHistogramModule.controller('ReclassifyHistogramController', ReclassifyHistogramController);

export default ReclassifyHistogramModule;

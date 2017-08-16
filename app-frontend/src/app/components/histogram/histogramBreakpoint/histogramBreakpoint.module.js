import angular from 'angular';

import HistogramBreakpoint from './histogramBreakpoint.component.js';
import HistogramBreakpointController from './histogramBreakpoint.controller.js';

const HistogramBreakpointModule = angular.module('components.histogram.histgramBreakpoint', []);

HistogramBreakpointModule.component('rfHistogramBreakpoint', HistogramBreakpoint);
HistogramBreakpointModule.controller(
    'HistogramBreakpointController', HistogramBreakpointController
);

export default HistogramBreakpointModule;

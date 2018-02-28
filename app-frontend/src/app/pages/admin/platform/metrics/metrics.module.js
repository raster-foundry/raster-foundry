import angular from 'angular';

class PlatformMetricsController {
    constructor() {
        // eslint-disable-next-line
        console.log('Platform Metrics Controller');
    }
}

const PlatformMetricsModule = angular.module('pages.platform.metrics', []);
PlatformMetricsModule.controller('PlatformMetricsController', PlatformMetricsController);

export default PlatformMetricsModule;

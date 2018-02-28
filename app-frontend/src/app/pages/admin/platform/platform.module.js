import angular from 'angular';

class PlatformController {
    constructor() {
        // eslint-disable-next-line
        console.log('Platform Controller');
    }
}

const PlatformModule = angular.module('pages.platform', []);
PlatformModule.controller('PlatformController', PlatformController);

export default PlatformModule;

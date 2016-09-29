import pagination from 'angular-ui-bootstrap/src/pagination';

import ScenesListController from './list.controller.js';

const ScenesListModule = angular.module('pages.library.scenes.list', [pagination]);

ScenesListModule.controller('ScenesListController', ScenesListController);

export default ScenesListModule;

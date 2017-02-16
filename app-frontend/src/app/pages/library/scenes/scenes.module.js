import pagination from 'angular-ui-bootstrap/src/pagination';

import ScenesController from './scenes.controller.js';

const ScenesModule = angular.module('pages.library.scenes', [pagination]);

ScenesModule.controller('ScenesController', ScenesController);

export default ScenesModule;

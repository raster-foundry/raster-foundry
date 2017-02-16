import pagination from 'angular-ui-bootstrap/src/pagination';

import SceneDetailController from './detail.controller.js';

const SceneDetailModule = angular.module('pages.library.scenes.detail', [pagination]);

SceneDetailModule.controller('SceneDetailController', SceneDetailController);

export default SceneDetailModule;

require('angular-ui-tree/source/angular-ui-tree.css');

import angular from 'angular';
import uiTree from 'angular-ui-tree';

import MosaicScenes from './mosaicScenes.component.js';
import MosaicScenesController from './mosaicScenes.controller.js';

const MosaicScenesModule = angular.module('components.mosaicScenes', [uiTree]);

MosaicScenesModule.component('rfMosaicScenes', MosaicScenes);
MosaicScenesModule.controller('MosaicScenesController', MosaicScenesController);

export default MosaicScenesModule;

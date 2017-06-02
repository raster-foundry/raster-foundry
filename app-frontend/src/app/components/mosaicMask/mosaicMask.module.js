import angular from 'angular';

import MosaicMask from './mosaicMask.component.js';
import MosaicMaskController from './mosaicMask.controller.js';

const MosaicMaskModule = angular.module('components.mosaicMask', []);

MosaicMaskModule.component('rfMosaicMask', MosaicMask);
MosaicMaskModule.controller('MosaicMaskController', MosaicMaskController);

export default MosaicMaskModule;

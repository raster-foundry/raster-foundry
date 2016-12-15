import angular from 'angular';

import MosaicMask from './mosaicMask.component.js';
import MosaicMaskController from './mosaicMask.controller.js';

require('./mosaicMask.scss');

const MosaicMaskModule = angular.module('components.mosaicMask', []);

MosaicMaskModule.component('rfMosaicMask', MosaicMask);
MosaicMaskModule.controller('MosaicMaskController', MosaicMaskController);

export default MosaicMaskModule;

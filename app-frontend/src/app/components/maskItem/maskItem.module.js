import angular from 'angular';

import MosaicMaskItem from './maskItem.component.js';
import MosaicMaskItemController from './maskItem.controller.js';

const MosaicMaskItemModule = angular.module('components.mosaicMaskItem', []);

MosaicMaskItemModule.component('rfMosaicMaskItem', MosaicMaskItem);
MosaicMaskItemModule.controller('MosaicMaskItemController', MosaicMaskItemController);

export default MosaicMaskItemModule;

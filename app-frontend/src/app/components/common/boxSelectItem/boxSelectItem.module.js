import angular from 'angular';
import BoxSelectItemComponent from './boxSelectItem.component.js';
import BoxSelectItemController from './boxSelectItem.controller.js';

const BoxSelectItemModule = angular.module('components.common.boxSelectItem', []);

BoxSelectItemModule.controller('BoxSelectItemController', BoxSelectItemController);
BoxSelectItemModule.component('rfBoxSelectItem', BoxSelectItemComponent);

export default BoxSelectItemModule;

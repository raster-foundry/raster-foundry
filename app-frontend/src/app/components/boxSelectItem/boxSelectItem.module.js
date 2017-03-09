import angular from 'angular';
import BoxSelectItemComponent from './boxSelectItem.component.js';
import BoxSelectItemController from './boxSelectItem.controller.js';
require('./boxSelectItem.scss');

const BoxSelectItemModule = angular.module('components.boxSelectItem', []);

BoxSelectItemModule.controller('BoxSelectItemController', BoxSelectItemController);
BoxSelectItemModule.component('rfBoxSelectItem', BoxSelectItemComponent);

export default BoxSelectItemModule;

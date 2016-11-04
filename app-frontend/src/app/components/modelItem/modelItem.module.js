import angular from 'angular';
import ModelItemComponent from './modelItem.component.js';
import ModelItemController from './modelItem.controller.js';

const ModelItemModule = angular.module('components.modelItem', []);

ModelItemModule.component('rfModelItem', ModelItemComponent);
ModelItemModule.controller('ModelItemController', ModelItemController);

export default ModelItemModule;

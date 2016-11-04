import angular from 'angular';

import ModelSearchComponent from './modelSearch.component.js';
import ModelSearchController from './modelSearch.controller.js';

const ModelSearchModule = angular.module('components.modelSearch', []);

ModelSearchModule.component('rfModelSearch', ModelSearchComponent);
ModelSearchModule.controller('ModelSearchController', ModelSearchController);

export default ModelSearchModule;

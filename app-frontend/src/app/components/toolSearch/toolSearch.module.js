import angular from 'angular';

import ToolSearchComponent from './toolSearch.component.js';
import ToolSearchController from './toolSearch.controller.js';

const ToolSearchModule = angular.module('components.toolSearch', []);

ToolSearchModule.component('rfToolSearch', ToolSearchComponent);
ToolSearchModule.controller('ToolSearchController', ToolSearchController);

export default ToolSearchModule;

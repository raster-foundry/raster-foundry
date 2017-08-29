import angular from 'angular';

import ReclassifyTableComponent from './reclassifyTable.component.js';
import ReclassifyTableController from './reclassifyTable.controller.js';

const ReclassifyTableModule = angular.module('components.tools.reclassifyTable', []);

ReclassifyTableModule.controller('ReclassifyTableController', ReclassifyTableController);
ReclassifyTableModule.component('rfReclassifyTable', ReclassifyTableComponent);

export default ReclassifyTableModule;

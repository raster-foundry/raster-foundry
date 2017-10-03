import angular from 'angular';

import ReclassifyEntryComponent from './reclassifyEntry.component.js';
import ReclassifyEntryController from './reclassifyEntry.controller.js';

const ReclassifyEntryModule = angular.module('components.tools.reclassifyEntry', []);

ReclassifyEntryModule.controller('ReclassifyEntryController', ReclassifyEntryController);
ReclassifyEntryModule.component('rfReclassifyEntry', ReclassifyEntryComponent);

export default ReclassifyEntryModule;


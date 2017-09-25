import angular from 'angular';

import ReclassifyEntryComponent from './reclassifyEntry.component.js';
import ReclassifyEntryController from './reclassifyEntry.controller.js';
import ValidateRangeString from './validateRangeString.directive.js';
import ValidateValueString from './validateValueString.directive.js';

const ReclassifyEntryModule = angular.module('components.tools.reclassifyEntry', []);

ReclassifyEntryModule.controller('ReclassifyEntryController', ReclassifyEntryController);
ReclassifyEntryModule.component('rfReclassifyEntry', ReclassifyEntryComponent);
ReclassifyEntryModule.directive('rfValidateRangeString', () => new ValidateRangeString());
ReclassifyEntryModule.directive('rfValidateValueString', () => new ValidateValueString());

export default ReclassifyEntryModule;


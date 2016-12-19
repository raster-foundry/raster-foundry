import angular from 'angular';

import LabEditController from './edit.controller.js';
import labEditComponent from './edit.component.js';

const labEditModule = angular.module('pages.lab.edit', []);

labEditModule.component('labEdit', labEditComponent);
labEditModule.controller('LabEditController', LabEditController);

export default labEditModule;

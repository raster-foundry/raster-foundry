import angular from 'angular';

import LabRunController from './run.controller.js';

const labRunModule = angular.module('pages.lab.run2', []);

labRunModule.controller('LabRunController2', LabRunController);

export default labRunModule;

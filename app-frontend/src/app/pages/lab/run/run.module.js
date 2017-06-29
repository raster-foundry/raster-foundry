import angular from 'angular';

import LabRunController from './run.controller.js';

const labRunModule = angular.module('pages.lab.run', []);

labRunModule.controller('LabRunController', LabRunController);

export default labRunModule;

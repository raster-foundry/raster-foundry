import angular from 'angular';

import LabRunController from './run.controller.js';
import labRunComponent from './run.component.js';


const labRunModule = angular.module('pages.lab.run', []);

labRunModule.component('labRun', labRunComponent);
labRunModule.controller('LabRunController', LabRunController);

export default labRunModule;

import angular from 'angular';
import ExportController from './export.controller.js';

const ExportModule = angular.module('page.projects.edit.export', []);

ExportModule.controller('ExportController', ExportController);

export default ExportModule;

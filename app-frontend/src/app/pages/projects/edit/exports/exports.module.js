import angular from 'angular';
import ExportController from './exports.controller.js';

const ExportModule = angular.module('page.projects.edit.exports', []);

ExportModule.controller('ExportController', ExportController);

export default ExportModule;

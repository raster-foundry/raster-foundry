import angular from 'angular';
import AnnotateImportController from './import.controller.js';
require('./import.scss');

const AnnotateImportModule = angular.module('pages.projects.edit.annotate.import', []);

AnnotateImportModule.controller(
    'AnnotateImportController', AnnotateImportController
);

export default AnnotateImportModule;

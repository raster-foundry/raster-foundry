import angular from 'angular';
import AnnotateExportController from './export.controller.js';

const AnnotateExportModule = angular.module('pages.projects.edit.annotate.export', []);

AnnotateExportModule.controller(
    'AnnotateExportController', AnnotateExportController
);

export default AnnotateExportModule;

import angular from 'angular';
import ExportModalComponent from './ExportModal.component.js';
import ExportModalController from './ExportModal.controller.js';
require('../../../assets/font/fontello/css/fontello.css');

const ExportModalModule = angular.module('components.ExportModal', []);

ExportModalModule.controller(
    'ExportModalController', ExportModalController
);
ExportModalModule.component(
    'rfExportModal', ExportModalComponent
);

export default ExportModalModule;

import angular from 'angular';
import ExportModalComponent from './exportModal.component.js';
import ExportModalController from './exportModal.controller.js';
require('../../../assets/font/fontello/css/fontello.css');

const ExportModalModule = angular.module('components.exportModal', []);

ExportModalModule.controller(
    'ExportModalController', ExportModalController
);
ExportModalModule.component(
    'rfExportModal', ExportModalComponent
);

export default ExportModalModule;

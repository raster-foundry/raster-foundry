import angular from 'angular';
import ExportDownloadModalComponent from './exportDownloadModal.component.js';
import ExportDownloadModalController from './exportDownloadModal.controller.js';

const ExportDownloadModalModule = angular.module('components.projects.exportDownloadModal', []);

ExportDownloadModalModule.controller(
    'ExportDownloadModalController', ExportDownloadModalController
);
ExportDownloadModalModule.component(
    'rfExportDownloadModal', ExportDownloadModalComponent
);

export default ExportDownloadModalModule;

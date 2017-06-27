import angular from 'angular';
import ExportItemComponent from './exportItem.component.js';
import ExportItemController from './exportItem.controller.js';

const ExportItemModule = angular.module('components.exports.exportItem', []);

ExportItemModule.component('rfExportItem', ExportItemComponent);
ExportItemModule.controller('ExportItemController', ExportItemController);

export default ExportItemModule;

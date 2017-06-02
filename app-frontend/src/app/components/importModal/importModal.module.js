import angular from 'angular';
import ImportModalComponent from './importModal.component.js';
import ImportModalController from './importModal.controller.js';

const ImportModalModule = angular.module('components.importModal', ['ngFileUpload']);

ImportModalModule.controller('ImportModalController', ImportModalController);
ImportModalModule.component('rfImportModal', ImportModalComponent);

export default ImportModalModule;

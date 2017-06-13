import angular from 'angular';
import DatasourceItemComponent from './datasourceItem.component.js';
import DatasourceItemController from './datasourceItem.controller.js';

const DatasourceItemModule = angular.module('components.datasourceItem', []);

DatasourceItemModule.controller('DatasourceItemController', DatasourceItemController);
DatasourceItemModule.component('rfDatasourceItem', DatasourceItemComponent);

export default DatasourceItemModule;

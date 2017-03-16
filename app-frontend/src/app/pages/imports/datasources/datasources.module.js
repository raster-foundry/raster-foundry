import DatasourcesController from './datasources.controller.js';
require('./datasources.scss');

const DatasourcesModule = angular.module('pages.imports.datasources', []);

DatasourcesModule.controller('DatasourcesController', DatasourcesController);

export default DatasourcesModule;

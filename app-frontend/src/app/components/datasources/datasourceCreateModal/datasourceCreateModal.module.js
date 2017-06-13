import angular from 'angular';
import DatasourceCreateModalComponent from './datasourceCreateModal.component.js';
import DatasourceCreateModalController from './datasourceCreateModal.controller.js';

const DatasourceCreateModalModule = angular.module(
    'components.datasources.datasourceCreateModal',
    []
);

DatasourceCreateModalModule.controller(
    'DatasourceCreateModalController',
    DatasourceCreateModalController
);

DatasourceCreateModalModule.component('rfDatasourceCreateModal', DatasourceCreateModalComponent);

export default DatasourceCreateModalModule;

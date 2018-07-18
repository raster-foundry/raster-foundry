import angular from 'angular';
import datasourceDeleteModalTpl from './datasourceDeleteModal.html';

const DatasourceDeleteModalComponent = {
    templateUrl: datasourceDeleteModalTpl,
    controller: 'DatasourceDeleteModalController',
    bindings: {
        resolve: '<',
        modalInstance: '<'
    }
};

class DatasourceDeleteModalController {
    constructor($rootScope, $scope, $log, uploadService) {
        'ngInject';
        $rootScope.autoInject(this, arguments);

        this.datasource = this.resolve.datasource;
    }

    $onInit() {
        this.checkUploadStatus();
    }

    checkUploadStatus() {
        this.uploadService.query({datasource: this.datasource.id}).then(res => {
            this.$log.log(res);
        });
    }
}

const DatasourceDeleteModalModule = angular.module('components.datasourceDeleteModal', []);

DatasourceDeleteModalModule.controller(
    'DatasourceDeleteModalController',
    DatasourceDeleteModalController
);
DatasourceDeleteModalModule.component('rfDatasourceDeleteModal', DatasourceDeleteModalComponent);

export default DatasourceDeleteModalModule;

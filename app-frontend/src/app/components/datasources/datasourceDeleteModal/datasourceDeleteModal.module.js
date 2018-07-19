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

const uploadProgress = 'CREATED,UPLOADING,UPLOADED,QUEUED,PROCESSING';

class DatasourceDeleteModalController {
    constructor($rootScope, $scope, $log, uploadService) {
        'ngInject';
        $rootScope.autoInject(this, arguments);

        this.datasource = this.resolve.datasource;
        this.uploadProgress = uploadProgress;
    }

    $onInit() {
        this.checkUploadStatus();
    }

    checkUploadStatus() {
        this.uploadService.query({
            datasource: this.datasource.id,
            uploadStatus: this.uploadProgress
        }).then(res => {
            if (res.count === 0) {
                this.checkSceneDatasource();
            } else {
                this.setUploadMsg();
            }
        });
    }

    checkSceneDatasource() {
        this.$log.log('check scene datasources');
    }

    setUploadMsg() {
        this.$log.log('show message for upload');
    }
}

const DatasourceDeleteModalModule = angular.module('components.datasourceDeleteModal', []);

DatasourceDeleteModalModule.controller(
    'DatasourceDeleteModalController',
    DatasourceDeleteModalController
);
DatasourceDeleteModalModule.component('rfDatasourceDeleteModal', DatasourceDeleteModalComponent);

export default DatasourceDeleteModalModule;

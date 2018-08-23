import angular from 'angular';
import datasourceDeleteModalTpl from './datasourceDeleteModal.html';

const DatasourceDeleteModalComponent = {
    templateUrl: datasourceDeleteModalTpl,
    controller: 'DatasourceDeleteModalController',
    bindings: {
        resolve: '<',
        modalInstance: '<',
        close: '&',
        dismiss: '&'
    }
};

const uploadProgress = 'CREATED,UPLOADING,UPLOADED,QUEUED,PROCESSING';

class DatasourceDeleteModalController {
    constructor(
        $rootScope, $scope, $log,
        uploadService, sceneService
    ) {
        'ngInject';
        $rootScope.autoInject(this, arguments);

        this.datasource = this.resolve.datasource;
        this.uploadProgress = uploadProgress;
    }

    $onInit() {
        this.checkUploadStatus();
    }

    checkUploadStatus() {
        this.checkInProgress = true;
        this.uploadService.query({
            datasource: this.datasource.id,
            uploadStatus: this.uploadProgress
        }).then(res => {
            if (res.count === 0) {
                this.checkSceneDatasource();
            } else {
                this.checkInProgress = false;
                this.allowDelete = false;
                this.displayUploadMsg(res.count);
            }
        });
    }

    checkSceneDatasource() {
        this.sceneService.query({
            datasource: this.datasource.id
        }).then(scenes => {
            this.checkInProgress = false;
            if (scenes.count !== 0) {
                this.displaySceneMsg(scenes.count);
            } else {
                this.deleteMsg = '<div class="color-danger">'
                    + 'You are about to delete this datasource. '
                    + 'This action is not reversible. '
                    + 'Are you sure you wish to continue?'
                    + '</div>';
            }
            this.allowDelete = true;
        });
    }

    displayUploadMsg(uploadCount) {
        const text = uploadCount === 1 ? 'upload is' : 'uploads are';
        this.deleteMsg = '<div class="color-danger">'
            + `<p>${uploadCount} in progress ${text} using this datasource.</p>`
            + '<p>Datasource cannot be deleted at this time.</p>'
            + '</div>';
    }

    displaySceneMsg(sceneCount) {
        const text = sceneCount === 1 ? 'scene is' : 'scenes are';
        this.deleteMsg = '<div class="color-danger">'
            + `<p>${sceneCount} ${text} using this datasource.</p>`
            + '<p>You are about to delete this datasource. '
            + 'Scenes using this datasource will no longer be accessible. '
            + 'This action is not reversible. '
            + 'Are you sure you wish to continue?</p>'
            + '</div>';
    }
}

const DatasourceDeleteModalModule = angular.module('components.datasourceDeleteModal', []);

DatasourceDeleteModalModule.controller(
    'DatasourceDeleteModalController',
    DatasourceDeleteModalController
);
DatasourceDeleteModalModule.component('rfDatasourceDeleteModal', DatasourceDeleteModalComponent);

export default DatasourceDeleteModalModule;

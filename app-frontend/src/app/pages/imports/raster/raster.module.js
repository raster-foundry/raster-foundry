/* global BUILDCONFIG, HELPCONFIG */
class RasterListController {
    constructor(
        $scope, $uibModal,
        authService, uploadService,
        platform
    ) {
        'ngInject';
        $scope.autoInject(this, arguments);
    }

    $onInit() {
        this.BUILDCONFIG = BUILDCONFIG;
        this.HELPCONFIG = HELPCONFIG;
        this.pendingImports = 0;
        this.checkPendingImports();
    }

    $onDestroy() {

    }

    importModal() {
        if (this.activeModal) {
            this.activeModal.dismiss();
        }

        this.activeModal = this.$uibModal.open({
            component: 'rfSceneImportModal',
            resolve: {
                origin: () => 'raster'
            }
        });

        this.activeModal.result.then(() => {
            this.checkPendingImports();
        });
    }

    checkPendingImports() {
        this.uploadService.query({
            uploadStatus: 'UPLOADED',
            pageSize: 0
        }).then(uploads => {
            this.pendingImports = uploads.count;
        });
    }

    openCreateDatasourceModal() {
        if (this.activeModal) {
            this.activeModal.dismiss();
        }

        this.activeModal = this.$uibModal.open({
            component: 'rfDatasourceCreateModal'
        });

        return this.activeModal;
    }
}

const RasterListModule = angular.module('pages.imports.rasters', []);

RasterListModule.controller('RasterListController', RasterListController);

export default RasterListModule;

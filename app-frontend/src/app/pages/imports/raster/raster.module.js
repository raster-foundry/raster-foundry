/* global BUILDCONFIG, HELPCONFIG */
class RasterListController {
    constructor(
        $scope, $state, modalService,
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
        this.currentOwnershipFilter = this.$state.params.ownership || 'owned';
    }

    importModal() {
        if (this.activeModal) {
            this.activeModal.dismiss();
        }

        this.modalService.open({
            component: 'rfSceneImportModal',
            resolve: {
                origin: () => 'raster'
            }
        }).result.then(() => {
            this.checkPendingImports();
        }).catch(() => {});
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
        this.modalService.open({
            component: 'rfDatasourceCreateModal'
        }).result.catch(() => {});
    }
}

const RasterListModule = angular.module('pages.imports.rasters', []);

RasterListModule.controller('RasterListController', RasterListController);

export default RasterListModule;

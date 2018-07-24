/* global BUILDCONFIG */
class RasterListController {
    constructor(authService, $uibModal, platform) {
        'ngInject';
        this.authService = authService;
        this.$uibModal = $uibModal;
        this.platform = platform;
    }

    $onInit() {
        this.BUILDCONFIG = BUILDCONFIG;
    }

    $onDestroy() {

    }

    importModal() {
        if (this.activeModal) {
            this.activeModal.dismiss();
        }

        this.activeModal = this.$uibModal.open({
            component: 'rfSceneImportModal',
            resolve: {}
        });
    }

    openCreateDatasourceModal() {
        if (this.activeModal) {
            this.activeModal.dismiss();
        }

        this.activeModal = this.$uibModal.open({
            component: 'rfDatasourceCreateModal'
        });

        this.activeModal.result.then(() => {

        });

        return this.activeModal;
    }
}

const RasterListModule = angular.module('pages.imports.rasters', []);

RasterListModule.controller('RasterListController', RasterListController);

export default RasterListModule;

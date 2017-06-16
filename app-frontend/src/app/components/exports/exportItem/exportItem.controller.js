export default class ExportItemController {
    constructor($scope, $uibModal, exportService) {
        'ngInject';
        this.$scope = $scope;
        this.$uibModal = $uibModal;
        this.exportService = exportService;
    }

    isDownloadAllowed() {
        return this.export.exportType === 'S3';
    }

    isDownloadReady() {
        return this.export.exportStatus === 'EXPORTED';
    }

    openDownloadModal() {
        if (this.activeModal) {
            this.activeModal.dismiss();
        }

        this.activeModal = this.$uibModal.open({
            component: 'rfExportDownloadModal',
            resolve: {
                export: () => this.export
            }
        });
    }
}

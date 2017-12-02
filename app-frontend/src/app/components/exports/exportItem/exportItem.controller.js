export default class ExportItemController {
    constructor($scope, modalService, exportService) {
        'ngInject';
        this.$scope = $scope;
        this.modalService = modalService;
        this.exportService = exportService;
    }

    isDownloadAllowed() {
        return this.export.exportType === 'S3';
    }

    isDownloadReady() {
        return this.export.exportStatus === 'EXPORTED';
    }

    openDownloadModal() {
        this.modalService.open({
            component: 'rfExportDownloadModal',
            resolve: {
                export: () => this.export
            }
        });
    }
}

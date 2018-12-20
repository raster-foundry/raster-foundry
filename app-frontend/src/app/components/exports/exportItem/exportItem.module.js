import angular from 'angular';
import exportTpl from './exportItem.html';

const ExportItemComponent = {
    templateUrl: exportTpl,
    transclude: true,
    controller: 'ExportItemController',
    bindings: {
        export: '<'
    }
};

class ExportItemController {
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
        }).result.catch(() => {});
    }
}

const ExportItemModule = angular.module('components.exports.exportItem', []);

ExportItemModule.component('rfExportItem', ExportItemComponent);
ExportItemModule.controller('ExportItemController', ExportItemController);

export default ExportItemModule;

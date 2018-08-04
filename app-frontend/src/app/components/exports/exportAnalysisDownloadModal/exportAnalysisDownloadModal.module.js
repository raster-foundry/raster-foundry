import angular from 'angular';
import _ from 'lodash';
import exportAnalysisDownloadModalTml from './exportAnalysisDownloadModal.html';

const ExportAnalysisDownloadModalComponent = {
    templateUrl: exportAnalysisDownloadModalTml,
    bindings: {
        close: '&',
        dismiss: '&',
        modalInstance: '<',
        resolve: '<'
    },
    controller: 'ExportAnalysisDownloadModalController'
};

class ExportAnalysisDownloadModalController {
    constructor($rootScope, $log, modalService) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    $postLink() {
        this.analysis = this.resolve.analysis;
        this.exports = this.resolve.exports;
    }

    isDownloadAllowed(thisExport) {
        return thisExport.exportType === 'S3' && thisExport.exportStatus === 'EXPORTED';
    }

    displayDownloadAction(thisExport) {
        if (thisExport.exportStatus === 'EXPORTED') {
            if (thisExport.exportType === 'S3') {
                return 'Download';
            } else if (thisExport.exportType === 'DROPBOX') {
                return 'On Dropbox';
            }
        } else if (thisExport.exportStatus === 'FAILED') {
            return 'Failed';
        }
        return 'In progress';
    }

    handleAnalysisDownload(thisExport) {
        this.modalService.open({
            component: 'rfExportDownloadModal',
            resolve: {
                export: () => thisExport
            }
        });
    }
}

const ExportAnalysisDownloadModalModule =
    angular.module('components.exports.exportAnalysisDownloadModal', []);

ExportAnalysisDownloadModalModule.controller(
    'ExportAnalysisDownloadModalController', ExportAnalysisDownloadModalController
);
ExportAnalysisDownloadModalModule.component(
    'rfExportAnalysisDownloadModal', ExportAnalysisDownloadModalComponent
);

export default ExportAnalysisDownloadModalModule;

// Component code
import exportDownloadModalTpl from './exportDownloadModal.html';

const ExportDownloadModalComponent = {
    templateUrl: exportDownloadModalTpl,
    bindings: {
        close: '&',
        dismiss: '&',
        modalInstance: '<',
        resolve: '<'
    },
    controller: 'ExportDownloadModalController'
};

export default ExportDownloadModalComponent;

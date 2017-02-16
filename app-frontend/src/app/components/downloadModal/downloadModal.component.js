import downloadModalTpl from './downloadModal.html';

const rfDownloadModal = {
    templateUrl: downloadModalTpl,
    bindings: {
        close: '&',
        dismiss: '&',
        modalInstance: '<',
        resolve: '<'
    }
};

export default rfDownloadModal;

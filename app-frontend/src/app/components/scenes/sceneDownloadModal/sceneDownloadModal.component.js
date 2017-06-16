import sceneDownloadModalTpl from './sceneDownloadModal.html';

const rfSceneDownloadModal = {
    templateUrl: sceneDownloadModalTpl,
    bindings: {
        close: '&',
        dismiss: '&',
        modalInstance: '<',
        resolve: '<'
    }
};

export default rfSceneDownloadModal;

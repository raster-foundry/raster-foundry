import sceneImportModalTpl from './sceneImportModal.html';

const rfSceneImportModal = {
    templateUrl: sceneImportModalTpl,
    bindings: {
        close: '&',
        dismiss: '&',
        modalInstance: '<',
        resolve: '<'
    },
    controller: 'ImportModalController'
};

export default rfSceneImportModal;

import sceneDetailModalTpl from './sceneDetailModal.html';

const rfSceneDetailModal = {
    templateUrl: sceneDetailModalTpl,
    bindings: {
        close: '&',
        dismiss: '&',
        modalInstance: '<',
        resolve: '<'
    },
    controller: 'SceneDetailModalController'
};

export default rfSceneDetailModal;

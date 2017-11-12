import planetSceneDetailModalTpl from './planetSceneDetailModal.html';

const rfPlanetSceneDetailModal = {
    templateUrl: planetSceneDetailModalTpl,
    bindings: {
        close: '&',
        dismiss: '&',
        modalInstance: '<',
        resolve: '<'
    },
    controller: 'PlanetSceneDetailModalController'
};

export default rfPlanetSceneDetailModal;

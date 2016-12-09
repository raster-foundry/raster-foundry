import mosaicScenesTpl from './mosaicScenes.html';

const mosaicScenes = {
    templateUrl: mosaicScenesTpl,
    controller: 'MosaicScenesController',
    bindings: {
        sceneList: '=',
        layers: '='
    }
};

export default mosaicScenes;

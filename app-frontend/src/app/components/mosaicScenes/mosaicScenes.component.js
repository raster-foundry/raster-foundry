import mosaicScenesTpl from './mosaicScenes.html';

const mosaicScenes = {
    templateUrl: mosaicScenesTpl,
    controller: 'MosaicScenesController',
    bindings: {
        sceneList: '=',
        layers: '=',
        onSceneMouseover: '&',
        onSceneMouseleave: '&'
    }
};

export default mosaicScenes;

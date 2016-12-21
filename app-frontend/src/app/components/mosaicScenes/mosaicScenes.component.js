import mosaicScenesTpl from './mosaicScenes.html';

const mosaicScenes = {
    templateUrl: mosaicScenesTpl,
    controller: 'MosaicScenesController',
    bindings: {
        sceneList: '=',
        sceneRequestState: '<',
        layers: '=',
        onSceneMouseover: '&',
        onSceneMouseleave: '&'
    }
};

export default mosaicScenes;

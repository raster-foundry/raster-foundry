import colorCorrectScenesTpl from './colorCorrectScenes.html';

const colorCorrectScenes = {
    templateUrl: colorCorrectScenesTpl,
    controller: 'ColorCorrectScenesController',
    bindings: {
        selectedScenes: '=',
        selectedLayers: '=',
        sceneList: '<',
        sceneLayers: '<',
        sceneRequestState: '<',
        layers: '<',
        onSceneMouseover: '&',
        onSceneMouseleave: '&'
    }
};

export default colorCorrectScenes;

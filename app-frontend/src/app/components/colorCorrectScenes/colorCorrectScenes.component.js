import colorCorrectScenesTpl from './colorCorrectScenes.html';

const colorCorrectScenes = {
    templateUrl: colorCorrectScenesTpl,
    controller: 'ColorCorrectScenesController',
    bindings: {
        selectedScenes: '=',
        selectedLayers: '=',
        sceneList: '=',
        sceneLayers: '=',
        layers: '='
    }
};

export default colorCorrectScenes;

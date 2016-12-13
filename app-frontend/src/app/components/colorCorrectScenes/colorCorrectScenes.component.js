import colorCorrectScenesTpl from './colorCorrectScenes.html';

const colorCorrectScenes = {
    templateUrl: colorCorrectScenesTpl,
    controller: 'ColorCorrectScenesController',
    bindings: {
        selectedScenes: '=',
        selectedLayers: '=',
        sceneList: '=',
        sceneLayers: '=',
        layers: '=',
        onSceneMouseover: '&',
        onSceneMouseleave: '&'
    }
};

export default colorCorrectScenes;

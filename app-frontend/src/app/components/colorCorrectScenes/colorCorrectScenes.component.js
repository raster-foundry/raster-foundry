import colorCorrectScenesTpl from './colorCorrectScenes.html';

const colorCorrectScenes = {
    templateUrl: colorCorrectScenesTpl,
    controller: 'ColorCorrectScenesController',
    bindings: {
        clickEvent: '<?',
        mapZoom: '<?',
        selectedScenes: '=',
        selectedLayers: '=',
        sceneList: '=',
        sceneLayers: '=',
        layers: '=',
        onSceneMouseover: '&',
        onSceneMouseleave: '&',
        newGridSelection: '&'
    }
};

export default colorCorrectScenes;

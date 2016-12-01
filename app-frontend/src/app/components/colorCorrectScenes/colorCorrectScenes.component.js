import colorCorrectScenesTpl from './colorCorrectScenes.html';

const colorCorrectScenes = {
    templateUrl: colorCorrectScenesTpl,
    controller: 'ColorCorrectScenesController',
    bindings: {
        sceneList: '<?',
        // Pass in a boolean to toggle to reset filters
        reset: '<?',
        onCorrectionChange: '&'
    }
};

export default colorCorrectScenes;

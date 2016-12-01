import mosaicScenesTpl from './mosaicScenes.html';

const mosaicScenes = {
    templateUrl: mosaicScenesTpl,
    controller: 'MosaicScenesController',
    bindings: {
        sceneList: '<?',
        // Pass in a boolean to toggle to reset filters
        reset: '<?',
        onCorrectionChange: '&'
    }
};

export default mosaicScenes;

import colorCorrectPaneTpl from './colorCorrectPane.html';

const colorCorrectPane = {
    templateUrl: colorCorrectPaneTpl,
    controller: 'ColorCorrectPaneController',
    bindings: {
        sceneList: '<?',
        // Pass in a boolean to toggle to reset filters
        reset: '<?',
        onCorrectionChange: '&',
        selectedLayers: '<?'
    }
};

export default colorCorrectPane;

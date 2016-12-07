import colorCorrectTpl from './colorCorrectPane.html';

const colorCorrectPane = {
    templateUrl: colorCorrectTpl,
    controller: 'ColorCorrectController',
    bindings: {
        desiredCorrection: '<?',
        // Pass in a boolean to toggle to reset filters
        reset: '<?',
        onCorrectionChange: '&',
        selectedLayers: '<?'
    }
};

export default colorCorrectPane;

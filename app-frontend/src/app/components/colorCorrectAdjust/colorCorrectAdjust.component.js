import colorCorrectAdjustTpl from './colorCorrectAdjust.html';

const colorCorrectAdjust = {
    templateUrl: colorCorrectAdjustTpl,
    controller: 'ColorCorrectAdjustController',
    bindings: {
        correction: '<?',
        // Pass in a boolean to toggle to reset filters
        reset: '<?',
        onCorrectionChange: '&'
    }
};

export default colorCorrectAdjust;

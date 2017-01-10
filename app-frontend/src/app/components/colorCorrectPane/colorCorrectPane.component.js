import colorCorrectPaneTpl from './colorCorrectPane.html';

const colorCorrectPane = {
    templateUrl: colorCorrectPaneTpl,
    controller: 'ColorCorrectPaneController',
    bindings: {
        selectedLayers: '<',
        mosaicLayer: '='
    }
};

export default colorCorrectPane;

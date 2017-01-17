import colorCorrectPaneTpl from './colorCorrectPane.html';

const colorCorrectPane = {
    templateUrl: colorCorrectPaneTpl,
    controller: 'ColorCorrectPaneController',
    bindings: {
        selectedLayers: '<',
        selectedScenes: '<',
        mosaicLayer: '='
    }
};

export default colorCorrectPane;

import mosaicMaskTpl from './mosaicMask.html';

const rfMosaicMask = {
    templateUrl: mosaicMaskTpl,
    controller: 'MosaicMaskController',
    bindings: {
        scene: '<',
        sceneList: '=',
        allowDrawing: '=',
        drawnPolygons: '=',
        layers: '='
    }
};

export default rfMosaicMask;

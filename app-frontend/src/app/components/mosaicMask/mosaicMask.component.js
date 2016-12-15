import mosaicMaskTpl from './mosaicMask.html';

const rfMosaicMask = {
    templateUrl: mosaicMaskTpl,
    controller: 'MosaicMaskController',
    bindings: {
        scene: '<',
        sceneList: '=',
        allowDrawing: '=',
        drawnPolygons: '=',
        mapExtent: '=',
        layers: '='
    }
};

export default rfMosaicMask;

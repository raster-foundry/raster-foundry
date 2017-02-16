import mosaicMaskTpl from './mosaicMask.html';

const rfMosaicMask = {
    templateUrl: mosaicMaskTpl,
    controller: 'MosaicMaskController',
    bindings: {
        scene: '<'
    }
};

export default rfMosaicMask;

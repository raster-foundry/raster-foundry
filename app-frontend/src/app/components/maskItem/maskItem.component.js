import mosaicMaskItemTpl from './maskItem.html';

const rfMosaicMaskItem = {
    templateUrl: mosaicMaskItemTpl,
    controller: 'MosaicMaskItemController',
    bindings: {
        mask: '<',
        onZoom: '&',
        onDelete: '&'
    }
};
export default rfMosaicMaskItem;


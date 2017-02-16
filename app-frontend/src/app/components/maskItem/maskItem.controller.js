export default class MosaicMaskItemController {
    constructor() {
    }

    onDeleteClick() {
        this.onDelete({mask: this.mask});
    }

    onZoomClick() {
        this.onZoom({mask: this.mask});
    }
}

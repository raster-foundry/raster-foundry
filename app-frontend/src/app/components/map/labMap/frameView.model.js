import Frame from './frame.model.js';
/**
 * FrameViews are frames which have Leaflet layers as children, instead of other Frames
 * On update, they update the clipping masks of their child layers
 */
export default class FrameView extends Frame {
    set children(children) {
        let nonLayerChildren = children.map(
            (child) => child instanceof L.Layer
        ).filter((isLayer) => !isLayer).length;
        if (nonLayerChildren > 0) {
            throw new Error('FrameViews can only contain L.Layer\'s as children');
        }
        this._children = children;
        this.update();
    }

    update() {
        if (!this.map) {
            return;
        }
        let dimensions = this.calculatedDimensions;
        $(this._container).css({
            width: dimensions.width,
            height: dimensions.height,
            top: dimensions.y,
            left: dimensions.x
            // transform: `translateX(${dimensions.rotation})`
        });

        // check for layer updates, remove any layers that are not on the map
        this._children = this._children.filter((layer) => this.map.hasLayer(layer));

        this._updateMask();
    }

    remove() {
        this._removeEvents();
        L.DomUtil.remove(this._container);
    }

    _updateMask() {
        // update mask on map
        let map = this.map;
        let dimensions = this.calculatedDimensions;
        let nw = map.containerPointToLayerPoint([
            dimensions.x, dimensions.y
        ]);
        let se = map.containerPointToLayerPoint([
            dimensions.x + dimensions.width, dimensions.y + dimensions.height
        ]);

        // Clip path is not supported in IE / Edge, so we can't use it for easy rotating frames
        let clipping = `rect(${[nw.y, se.x, se.y, nw.x].join('px,')}px)`;
        this._children.forEach((layer) => {
            layer.getContainer().style.clip = clipping;
        });
    }
}

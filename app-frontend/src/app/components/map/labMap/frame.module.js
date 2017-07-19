import Frame from './frame.model.js';
import FrameView from './frameView.model.js';

/*
  Ideas:
  A) Frames can have child frames.
     Pros:
     * Purpose built for what we need
     * Easier to use
     Cons:
     * Less flexible
  B) Frames are independent, and relationships between them are managed by the controller
     Pros:
     * more flexible - mostly just an easy way of handling masks on the map for certain layers.
     * Could be extended for all sorts of stuff. Examples:
       - Select an area to see a different set of layers there
       - a window you can drag around to compare layers
     Cons:
     * Harder to use - much of the controlling logic will need to be
       delegated to the controller using it
     *
  */

L.Control.Frames = L.Control.extend(
    {
        options: {
            padding: 0
        },

        initialize: function (options) {
            this._frame = new Frame();
            L.setOptions(this, options);
        },

        includes: L.Mixin.Events,

        getFrame: function () {
            return this._frame;
        },

        addTo: function (map) {
            this.remove();
            this._map = map;
            this._frame.map = map;
            this._addEvents();
            return this;
        },

        remove: function () {
            if (!this._map) {
                return this;
            }
            this._frame.remove();

            this._removeEvents();
            this._map = null;
            return this;
        },

        _updateFrameDimensions: function () {
            let mapSize = this._map.getSize();
            this._frame.dimensions = {
                x: 0,
                y: 0,
                width: mapSize.x,
                height: mapSize.y
            };
        },

        _addEvents: function () {
            this._map.on('move', this._updateFrameDimensions, this);
        },

        _removeEvents: function () {
            this._map.off('move', this._updateFrameDimensions, this);
        }
    }
);

L.control.frames = function (options) {
    return new L.Control.Frames(options);
};

export {Frame, FrameView};
export default L.Control.Frames;

let mapWasDragEnabled;
let mapWasTapEnabled;

// Leaflet v0.7 backwards compatibility
function on(el, types, fn, context) {
    types.split(' ').forEach(function (type) {
        L.DomEvent.on(el, type, fn, context);
    });
}

// Leaflet v0.7 backwards compatibility
function off(el, types, fn, context) {
    types.split(' ').forEach(function (type) {
        L.DomEvent.off(el, type, fn, context);
    });
}

function getRangeEvent(rangeInput) {
    return 'oninput' in rangeInput ? 'input' : 'change';
}

function cancelMapDrag() {
    mapWasDragEnabled = mapWasDragEnabled || this._map.dragging.enabled();
    mapWasTapEnabled = this._map.tap && this._map.tap.enabled();
    this._map.dragging.disable();
    if (this._map.tap) {
        this._map.tap.disable();
    }
}

function uncancelMapDrag(e) {
    this._refocusOnMap(e);
    if (mapWasDragEnabled) {
        this._map.dragging.enable();
        mapWasDragEnabled = false;
    }
    if (mapWasTapEnabled) {
        this._map.tap.enable();
    }
}

// convert arg to an array - returns empty array if arg is undefined
function asArray(arg) {
    if (typeof arg === 'undefined') {
        return [];
    }
    return Array.isArray(arg) ? arg : [arg];
}

L.Control.SideBySide = L.Control.extend(
    {
        options: {
            thumbSize: 42,
            padding: 0
        },

        initialize: function (leftLayers, rightLayers, options) {
            this.setLeftLayers(leftLayers);
            this.setRightLayers(rightLayers);
            L.setOptions(this, options);
        },

        getPosition: function () {
            let rangeValue = this._range.value;
            let offset = (0.5 - rangeValue) * (2 * this.options.padding + this.options.thumbSize);
            return this._map.getSize().x * rangeValue + offset;
        },

        setPosition: () => {},

        includes: L.Mixin.Events,

        addTo: function (map) {
            this.remove();
            this._map = map;

            let container = this._container = L.DomUtil.create(
                // eslint-disable-next-line no-underscore-dangle
                'div', 'leaflet-sbs', map._controlContainer
            );

            this._divider = L.DomUtil.create('div', 'leaflet-sbs-divider', container);
            let range = this._range = L.DomUtil.create('input', 'leaflet-sbs-range', container);
            range.type = 'range';
            range.min = 0;
            range.max = 1;
            range.step = 'any';
            range.value = 0.5;
            range.style.paddingLeft = range.style.paddingRight = this.options.padding + 'px';
            this._addEvents();
            this._updateLayers();
            return this;
        },

        remove: function () {
            if (!this._map) {
                return this;
            }
            this._removeEvents();
            L.DomUtil.remove(this._container);

            this._map = null;

            return this;
        },

        setLeftLayers: function (leftLayers) {
            this._leftLayers = asArray(leftLayers);
            this._updateLayers();
            return this;
        },

        setRightLayers: function (rightLayers) {
            this._rightLayers = asArray(rightLayers);
            this._updateLayers();
            return this;
        },

        _updateClip: function () {
            let map = this._map;
            let nw = map.containerPointToLayerPoint([0, 0]);
            let se = map.containerPointToLayerPoint(map.getSize());
            let clipX = nw.x + this.getPosition();
            let dividerX = this.getPosition();

            this._divider.style.left = dividerX + 'px';
            this.fire('dividermove', {x: dividerX});
            let clipLeft = 'rect(' + [nw.y, clipX, se.y, nw.x].join('px,') + 'px)';
            let clipRight = 'rect(' + [nw.y, se.x, se.y, clipX].join('px,') + 'px)';
            if (this._leftLayer) {
                this._leftLayer.getContainer().style.clip = clipLeft;
            }
            if (this._rightLayer) {
                this._rightLayer.getContainer().style.clip = clipRight;
            }
        },

        _updateLayers: function () {
            if (!this._map) {
                return this;
            }
            let prevLeft = this._leftLayer;
            let prevRight = this._rightLayer;
            this._leftLayer = this._rightLayer = null;
            this._leftLayers.forEach(function (layer) {
                if (this._map.hasLayer(layer)) {
                    this._leftLayer = layer;
                }
            }, this);
            this._rightLayers.forEach(function (layer) {
                if (this._map.hasLayer(layer)) {
                    this._rightLayer = layer;
                }
            }, this);
            if (prevLeft !== this._leftLayer) {
                if (prevLeft) {
                    this.fire('leftlayerremove', {layer: prevLeft});
                }
                if (this._leftLayer) {
                    this.fire('leftlayeradd', {layer: this._leftLayer});
                }
            }
            if (prevRight !== this._rightLayer) {
                if (prevRight) {
                    this.fire('rightlayerremove', {layer: prevRight});
                }
                if (this._rightLayer) {
                    this.fire('rightlayeradd', {layer: this._rightLayer});
                }
            }
            this._updateClip();

            return this;
        },

        _addEvents: function () {
            let range = this._range;
            let map = this._map;
            if (!map || !range) {
                return;
            }
            map.on('move', this._updateClip, this);
            map.on('layeradd layerremove', this._updateLayers, this);
            on(range, getRangeEvent(range), this._updateClip, this);
            on(range, 'mousedown touchstart', cancelMapDrag, this);
            on(range, 'mouseup touchend', uncancelMapDrag, this);
        },

        _removeEvents: function () {
            let range = this._range;
            let map = this._map;
            if (range) {
                off(range, getRangeEvent(range), this._updateClip, this);
                off(range, 'mousedown touchstart', cancelMapDrag, this);
                off(range, 'mouseup touchend', uncancelMapDrag, this);
            }
            if (map) {
                map.off('layeradd layerremove', this._updateLayers, this);
                map.off('move', this._updateClip, this);
            }
        }
    });

L.control.sideBySide = function (leftLayers, rightLayers, options) {
    return new L.Control.SideBySide(leftLayers, rightLayers, options);
};

export default L.Control.SideBySide;

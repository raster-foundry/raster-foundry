import Divider from './divider.model.js';
const defaultOptions = {
    classes: []
};
/**
  * Frames can contain any number of child Frames.
  * Base frames are only concerned with their layout, and the layout of their children
  * That includes: things like drag to resize, position, rotate, etc
  */
export default class Frame {
    constructor(options) {
        this._children = [];
        this._dividers = [];
        // rotation affects the divider's angle
        this._dimensions = {
            x: 0,
            y: 0,
            width: 0,
            height: 0,
            rotation: 0
        };
        if (options) {
            this._options = Object.assign({}, defaultOptions, options);
        } else {
            this._options = Object.assign({}, defaultOptions);
        }
    }

    addTo(map) {
        if (this.parent) {
            throw new Error('Only the top level frame should be added to a map');
        }
        this._map = map;

        this._container = L.DomUtil.create(
            // eslint-disable-next-line no-underscore-dangle
            'div', 'leaflet-frame', map._controlContainer
        );

        this._addEvents();
    }

    remove() {
        this._removeEvents();
        L.DomUtil.remove(this._container);
        this._children.forEach((child) => child.remove());
        this._dividers.forEach((divider) => divider.remove());
    }

    get map() {
        if (this._parent) {
            return this._parent.map;
        }
        return this._map;
    }

    set map(map) {
        this.addTo(map);
    }

    get options() {
        return this._options;
    }

    // TODO implement setting options without resetting everything
    // set options(options) {
    //     this._options = Object.assign({}, defaultOptions, options);
    //     this.parent = this.parent;
    //     this.update();
    // }

    set parent(frame) {
        if (this._parent) {
            this.remove();
        }
        this._parent = frame;
        if (!frame) {
            L.DomUtil.remove(this._container);
            this.removeEvents();
            delete this._container;
        } else {
            this._container = L.DomUtil.create(
                // eslint-disable-next-line no-underscore-dangle
                'div', `leaflet-frame ${this.options.classes.join(' ')}`, frame._container
            );
        }
    }

    get parent() {
        return this._parent;
    }

    get dimensions() {
        return this._dimensions;
    }

    set dimensions(dimensions) {
        this._dimensions = dimensions;
        this.update();
    }

    get calculatedDimensions() {
        if (this.parent) {
            let parentDimensions = this.parent.dimensions;
            return Object.assign(this._dimensions, {
                x: parentDimensions.x + this._dimensions.x,
                y: parentDimensions.y + this._dimensions.y
            });
        }
        return this._dimensions;
    }

    get children() {
        return this._children;
    }

    set children(children) {
        let nonFrameChildren = children.map(
            (child) => child instanceof Frame
        ).filter((isLayer) => !isLayer).length;
        if (nonFrameChildren > 0) {
            throw new Error('Frames can only contain other Frames as children');
        }
        this._children = children;
        this._children.forEach((child) => {
            child.parent = this;
        });

        // remove old dividers
        this._dividers.forEach((divider) => {
            divider.remove();
        });
        this._dividers = [];

        // add new dividers
        for (let i = 1; i < this._children.length; i = i + 1) {
            let divider = new Divider(
                this.dimensions.width / this._children.length * i / this.dimensions.width
            );
            divider.frame = this;
            divider.onPositionChange = this.update.bind(this);
            this._dividers.push(divider);
        }
        this.update();
    }

    set onUpdate(listener) {
        this._onUpdate = listener;
    }

    get onUpdate() {
        return this._onUpdate;
    }

    update() {
        if (!this.map) {
            return;
        }

        if (this.onUpdate) {
            this.onUpdate(this._dividers);
        }

        // update element dimensions to match dimensions, if they are different
        let dimensions = this.calculatedDimensions;
        $(this._container).css({
            width: dimensions.width,
            height: dimensions.height,
            top: dimensions.y,
            left: dimensions.x
            // transform: `translateX(${dimensions.rotation})`
        });

        // update children
        for (let i = 0; i < this._children.length; i = i + 1) {
            let child = this._children[i];

            let x = dimensions.width / this._children.length * i;
            let width = dimensions.width / this._children.length;
            if (this._dividers.length) {
                if (i === 0) {
                    x = 0;
                    width = dimensions.width * this._dividers[0].position;
                } else if (i < this._dividers.length - 1) {
                    x = this._dividers[i - 1].position * dimensions.width;
                    width = (
                        this._dividers[i].position - this._dividers[i - 1].position
                    ) * dimensions.width;
                } else {
                    x = this._dividers[i - 1].position * dimensions.width;
                    width = (1 - this._dividers[i - 1].position) * dimensions.width;
                }
            }

            child.dimensions = Object.assign(
                child.dimensions,
                {
                    x: x,
                    y: 0,
                    width: width,
                    height: dimensions.height
                }
            );

            // update child
            child.update();
        }
    }

    // Events should only be handled on the top level frame - any events will be propagated down
    //   to child frames
    _addEvents() {
        this.map.on('move layeradd layerremove', this.update, this);
    }

    _removeEvents() {
        this.map.off('move layeradd layerremove', this.update, this);
    }
}

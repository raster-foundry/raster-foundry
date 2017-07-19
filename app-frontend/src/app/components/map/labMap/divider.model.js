export default class Divider {
    constructor(position, options) {
        this._position = position ? position : 0.5;
        this._options = options ? options : {dividerClasses: [], grabberClasses: []};
        this._dragging = false;
    }

    get options() {
        return this._options;
    }

    // TODO: implement setting options without resetting everything
    // set options(options) {
    //     this._options = options ? options : {dividerClasses: [], grabberClasses: []};
    //     // update options
    //     this.frame = this.frame;
    // }

    get frame() {
        return this._frame;
    }

    set frame(frame) {
        if (this._frame) {
            this.remove();
        }
        this._frame = frame;
        /* eslint-disable */
        this._container = L.DomUtil.create(
            'div',
            `leaflet-frame-divider ${
                this._options.dividerClasses ? this._options.dividerClasses.join(' ') : ''
            }`, this._frame._container
        );
        /* eslint-enable */
        this._grabber = L.DomUtil.create(
            'div',
            `leaflet-frame-grabber ${
              this._options.grabberClasses ? this._options.grabberClasses.join(' ') : ''}`,
            this._container
        );
        L.DomUtil.create('span', 'icon-gripper', this._grabber);
        this._addEvents();
        this.update();
    }

    get position() {
        return this._position;
    }

    set position(position) {
        this._position = position;
        this.update();
    }

    set onPositionChange(callback) {
        this._positionCallback = callback;
    }

    get onPositionChange() {
        return this._positionCallback;
    }

    updatePosition(event) {
        const lastPosition = this._position;
        this._position = event.offsetX / this._frame.dimensions.width;
        event.preventDefault();
        event.stopPropagation();
        if (lastPosition !== this._position) {
            this.update();
        }
    }

    remove() {
        this._removeEvents();
        L.DomUtil.remove(this._container);
        if (this.dragging) {
            L.DomEvent.off(this._frame.map, 'mousemove', this.update, this);
        }
        delete this._frame;
    }

    _addEvents() {
        L.DomEvent.on(this._grabber, 'mousedown touchstart', this._startResize, this);
        L.DomEvent.on(this._grabber, 'mouseup touchend', this._endResize, this);
    }

    _removeEvents() {
        delete this._positionCallback;
        L.DomEvent.off(this._grabber, 'mousedown touchstart', this._startResize, this);
        L.DomEvent.off(this._grabber, 'mouseup touchend', this._endResize, this);
    }

    update() {
        $(this._container).css({
            left: `${this._position * 100}%`,
            top: 0,
            height: '100%',
            width: '2px'
        });
        if (this.onPositionChange) {
            this.onPositionChange(this._position);
        }
    }

    _startResize() {
        if (this._frame.map.dragging.enabled()) {
            this._frame.map.dragging.disable();
        }
        if (this._frame.map.tap) {
            this._tapDisabled = true;
            this._frame.map.tap.disable();
        }
        $(this._grabber).css({
            'pointer-events': 'none'
        });
        // eslint-disable-next-line
        $(this._frame._container).css({
            'pointer-events': 'initial'
        });
        this._resizing = true;
        // eslint-disable-next-line
        L.DomEvent.on(this._frame._container, 'mousemove', this.updatePosition, this);
        L.DomEvent.on(this._frame.map, 'mouseup touchend mouseleave', this._endResize, this);
    }

    _endResize() {
        this._resizing = false;
        this._frame.map.dragging.enable();
        if (this._tapDisabled) {
            delete this._tapDisabled;
            this._frame.map.tap.enable();
        }
        $(this._grabber).css({
            'pointer-events': ''
        });

        // eslint-disable-next-line
        $(this._frame._container).css({
            'pointer-events': ''
        });
        // eslint-disable-next-line
        L.DomEvent.off(this._frame._container, 'mousemove', this.updatePosition, this);
        L.DomEvent.off(this._frame.map, 'mouseup touchend mouseleave', this._endResize, this);
    }
}

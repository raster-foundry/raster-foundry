import _ from 'lodash';
import Konva from 'konva';

/* eslint no-unused-vars: 0 */
/* eslint spaced-comment: 0 */

export default (app) => {
    class ImageOverlayService {

        /** Returns a new Image overlay layer that clips to a datamask
         *
         * @param {string} url location to request tiles from
         * @param {L.Bounds} bounds leaflet bounds for image
         * @param {Object} options additional parameters to create image overlay
         *
         * @returns {L.Layer} image layer to put on map for thumbnails
         */
        createNewImageOverlay(url, bounds, options) {
            let ImageOverlayLayer = L.ImageOverlay.extend({
                options: {
                    opacity: 1,
                    alt: '',
                    interactive: false,
                    crossOrigin: false,
                    dataMask: {},
                    thumbnail: ''
                },

                _initImage: function () {
                    this._image = L.DomUtil.create('div', 'leaflet-image-layer ' +
                        (this._zoomAnimated ? 'leaflet-zoom-animated' : ''));
                },

                _reset: function () {
                    let tile = this._image;
                    let tileBounds = new L.Bounds(
                        this._map.latLngToLayerPoint(this._bounds.getNorthWest()),
                        this._map.latLngToLayerPoint(this._bounds.getSouthEast())
                        );
                    let size = this.size = tileBounds.getSize();

                    L.DomUtil.setPosition(tile, tileBounds.min);

                    tile.style.width = size.x + 'px';
                    tile.style.height = size.y + 'px';

                    let dataMask = this.options.dataMask;

                    let result = dataMask.coordinates[0][0].map((lngLat) => {
                        let point = this._map.latLngToLayerPoint([lngLat[1], lngLat[0]],
                            this._map.getZoom()
                        );
                        return [point.x - tileBounds.min.x, point.y - tileBounds.min.y];
                    });

                    let points = _.flatten(result);


                    let stage = this.stage = new Konva.Stage({
                        container: tile,
                        width: size.x,
                        height: size.y
                    });

                    let layer = this.layer = new Konva.Layer();

                    let img = this.img = L.DomUtil.create('img');
                    img.src = this.options.thumbnail;

                    let lineConfig = {
                        points: points,
                        width: size.x,
                        height: size.y,
                        fillPriority: 'pattern',
                        fillPatternImage: img,
                        fillPatternRepeat: 'no-repeat',
                        fillPatternX: 0,
                        fillPatternY: 0,
                        closed: true
                    };

                    this.lineImage = new Konva.Line(lineConfig);

                    // @TODO: Replace once #897 is closed
                    img.onload = () => {
                        let scale = {
                            x: this.size.x / this.img.naturalWidth,
                            y: this.size.y / this.img.naturalHeight
                        };
                        this.lineImage.fillPatternScale(scale);
                        this.layer.add(this.lineImage);
                        this.layer.draw();
                    };
                    stage.add(layer);
                },
                onRemove: function () {
                    L.DomUtil.remove(this._image);
                    if (this.options.interactive) {
                        this.removeInteractiveTarget(this._image);
                    }
                    this.stage.destroy();
                    this.layer.destroy();
                    this.lineImage.destroy();
                }

            });

            return new ImageOverlayLayer(url, bounds, options);
        }
    }

    app.service('imageOverlayService', ImageOverlayService);
};

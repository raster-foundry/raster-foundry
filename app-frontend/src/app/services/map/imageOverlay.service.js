import _ from 'lodash';
import d3 from 'd3';

/* eslint no-unused-vars: 0 */
/* eslint spaced-comment: 0 */

export default (app) => {
    class ImageOverlayService {

        constructor(uuid4) {
            this.uuid4 = uuid4;
        }

        /** Returns a new Image overlay layer that clips to a datamask
         *
         * @param {string} url location to request tiles from
         * @param {L.Bounds} bounds leaflet tileBounds for image
         * @param {Object} options additional parameters to create image overlay
         *
         * @returns {L.Layer} image layer to put on map for thumbnails
         */
        createNewImageOverlay(url, bounds, options) {
            let uuid4 = this.uuid4;
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
                    // create layer container element
                    this._image = L.DomUtil.create('div', 'leaflet-image-layer ' +
                        (this._zoomAnimated ? 'leaflet-zoom-animated' : ''));
                    // create svg
                    let svg = this.svg = d3.select(this._image).append('svg');
                    // create defs - clipping will go here
                    let defs = svg.append('defs');

                    // create unique id for clipping
                    this.id = uuid4.generate();

                    // calculate image element coordinate etc
                    let tileBounds = new L.Bounds(
                        this._map.latLngToLayerPoint(this._bounds.getNorthWest()),
                        this._map.latLngToLayerPoint(this._bounds.getSouthEast())
                    );
                    let size = this.size = tileBounds.getSize();

                    svg.attr('width', size.x)
                        .attr('height', size.y)
                        .attr('viewBox', `0 0 ${size.x} ${size.y}`);
                    // create image element
                    let img = this.img = svg.append('svg:image')
                        .attr('x', 0)
                        .attr('y', 0)
                        .attr('width', size.x)
                        .attr('height', size.y)
                        .attr('clip-path', `url(#${this.id})`)
                        .attr('href', this.options.thumbnail);

                    // add mask data
                    let dataMask = this.options.dataMask;

                    let results = dataMask.coordinates.map(coords => {
                        return coords[0]
                            .map((lngLat) => {
                                const point = this._map.latLngToLayerPoint(
                                    [lngLat[1], lngLat[0]], this._map.getZoom()
                                );
                                return [point.x - tileBounds.min.x, point.y - tileBounds.min.y];
                            })
                            .map((point) => `${point[0]},${point[1]}`).join(' ');
                    });

                    let clipPath = defs.append('clipPath')
                        .attr('id', this.id);

                    results.forEach(r => {
                        clipPath
                            .append('polygon')
                            .attr('points', r);
                    });
                },

                _reset: function () {
                    let tileBounds = new L.Bounds(
                        this._map.latLngToLayerPoint(this._bounds.getNorthWest()),
                        this._map.latLngToLayerPoint(this._bounds.getSouthEast())
                    );
                    let size = this.size = tileBounds.getSize();
                    // update container size
                    this._image.style.width = `${size.x}px`;
                    this._image.style.height = `${size.y}px`;
                    L.DomUtil.setPosition(this._image, tileBounds.min);
                    // update svg size and location
                    this.svg.attr('width', size.x)
                        .attr('height', size.y);
                },
                onRemove: function () {
                    L.DomUtil.remove(this._image);
                    if (this.options.interactive) {
                        this.removeInteractiveTarget(this._image);
                    }
                }

            });

            return new ImageOverlayLayer(url, bounds, options);
        }
    }

    app.service('imageOverlayService', ImageOverlayService);
};

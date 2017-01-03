import Konva from 'konva';

export default (app) => {

    /** Service to create a Leaflet grid layer for scenes
     */
    class GridLayerService {
        constructor($http) {
            'ngInject';
            this.$http = $http;
        }

        /** Request grid from grom scene grid endpoint
         *
         * @param {object} coords coordinates to request grid for
         * @param {object} params additional filter parameters for scene grid
         *
         * @returns {promise} API response for scene grid
         */
        requestGrid(coords, params) {
            let url = `/api/scene-grid/${coords.z}/${coords.x}/${coords.y}/`;
            return this.$http.get(url, {params: params});
        }

        /** Create grid layer given a set of filter parameters
         *
         * @param {object} params filter parameters for scene grid
         *
         * @returns {GridLayer} grid layer used to control display of scene summary for tiles
         */
        createNewGridLayer(params) {
            let self = this;

            delete params.bbox;

            let GridLayer = L.GridLayer.extend({

                requestGrid: self.requestGrid.bind(self),

                params: params,

                /** Function called when parameters have been updated to force a redraw
                 *
                 * @param {object} updateParams updated filter parameters for layer
                 *
                 * @returns {null} null
                 */
                updateParams: function (updateParams) {
                    delete params.bbox;
                    this.params = updateParams;
                    this.redraw();
                },

                /** Function to calculate shade of grid cell based on number of contained scenes
                 *
                 * @param {number} number count of scenes, used to determine shade
                 * @returns {string} rgba formatted string for scene grid color
                 */
                getColor: (number) => {
                    if (number === 0) {
                        return 'rgba(53,60,88,0)';
                    } else if (number > 0 && number < 5) {
                        return 'rgba(53,60,88,.15)';
                    } else if (number >= 5 && number < 30) {
                        return 'rgba(53,60,88,.35)';
                    } else if (number >= 30 && number < 60) {
                        return 'rgba(53,60,88,.60)';
                    }
                    return 'rgba(53,60,88,.70)';
                },

                /** Function used to create tile for custom grid layer
                 *
                 * @param {object} coords upper left coordinates of tile
                 * @param {function} done callback used to show tile generation finished
                 * @return {div} tile container
                 */
                createTile: function (coords, done) {
                    let tile = L.DomUtil.create('div', 'leaflet-tile');

                    let stage = new Konva.Stage({
                        container: tile,
                        width: 256,
                        height: 256
                    });

                    let text = new Konva.Text({
                        x: 0,
                        y: 0,
                        align: 'center',
                        fontFamily: 'Titillium Web',
                        strokeHitEnabled: false,
                        fontSize: 12,
                        text: '',
                        fill: 'black'
                    });

                    let layer = new Konva.Layer();

                    function writeMessage(number, x, y) {
                        text.setText(number);
                        text.setAttrs({x: x - text.getWidth() / 2,
                                       y: y - text.getHeight() / 2});
                        layer.draw();
                    }

                    let topLeft = new Konva.Rect({
                        x: 0,
                        y: 0,
                        width: 127,
                        height: 127
                    });

                    let topRight = new Konva.Rect({
                        x: 128,
                        y: 0,
                        width: 127,
                        height: 127
                    });

                    let bottomLeft = new Konva.Rect({
                        x: 0,
                        y: 128,
                        width: 127,
                        height: 127
                    });

                    let bottomRight = new Konva.Rect({
                        x: 128,
                        y: 128,
                        width: 127,
                        height: 127
                    });
                    layer.add(topLeft);
                    layer.add(bottomLeft);
                    layer.add(topRight);
                    layer.add(bottomRight);
                    layer.add(text);

                    this.requestGrid(coords, this.params).then((result) => {
                        let data = result.data;
                        topLeft.on('mouseover', function () {
                            writeMessage(data[0], 64, 64);
                        });
                        topLeft.on('mouseout', function () {
                            writeMessage('', 64, 64);
                        });
                        topLeft.fill(this.getColor(data[0]));

                        topRight.on('mouseover', function () {
                            writeMessage(data[1], 192, 64);
                        });
                        topRight.on('mouseout', function () {
                            writeMessage('', 192, 64);
                        });
                        topRight.fill(this.getColor(data[1]));

                        bottomLeft.on('mouseover', function () {
                            writeMessage(data[3], 64, 192);
                        });
                        bottomLeft.on('mouseout', function () {
                            writeMessage('', 64, 192);
                        });
                        bottomLeft.fill(this.getColor(data[3]));

                        bottomRight.on('mouseover', function () {
                            writeMessage(data[2], 192, 192);
                        });
                        bottomRight.on('mouseout', function () {
                            writeMessage('', 192, 192);
                        });
                        bottomRight.fill(this.getColor(data[2]));

                        stage.add(layer);
                        done(null, tile);
                    });
                    return tile;
                }
            });

            return new GridLayer();
        }

    }

    app.service('gridLayerService', GridLayerService);
};

/* globals BUILDCONFIG */

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
            let url = `${BUILDCONFIG.API_HOST}/api/scene-grid/${coords.z}/${coords.x}/${coords.y}/`;
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
            let gridParams = Object.assign(params);

            delete gridParams.bbox;

            let GridLayer = L.GridLayer.extend({

                requestGrid: self.requestGrid.bind(self),

                params: gridParams,

                /** Function called when parameters have been updated to force a redraw
                 *
                 * @param {object} updateParams updated filter parameters for layer
                 *
                 * @returns {null} null
                 */
                updateParams: function (updateParams) {
                    // Hacky way to determine param object equality because JS:
                    // Ignore bbox and null parameters and handles arrays/lists
                    function paramsAreEqual(oldParams, newParams) {
                        let newParamsArray = Object.getOwnPropertyNames(newParams)
                            .filter((prop) => {
                                return newParams[prop] !== null && prop !== 'bbox';
                            })
                            .map((prop) => {
                                return [prop, newParams[prop]];
                            });
                        let oldParamsArray = Object.getOwnPropertyNames(oldParams)
                            .filter((prop) => {
                                return oldParams[prop] !== null && prop !== 'bbox';
                            })
                            .map((prop) => {
                                return [prop, oldParams[prop]];
                            });
                        return JSON.stringify(newParamsArray.sort()) ===
                            JSON.stringify(oldParamsArray.sort());
                    }

                    if (!paramsAreEqual(this.params, updateParams)) {
                        this.params = Object.assign(updateParams);
                        this.redraw();
                    }
                },

                /** Function to calculate shade of grid cell based on number of contained scenes
                 *
                 * @param {number} number count of scenes, used to determine shade
                 * @returns {string} rgba formatted string for scene grid color
                 */
                getColor: (number) => {
                    if (number === 0) {
                        return 'rgba(53,60,88,.05)';
                    } else if (number > 0 && number < 5) {
                        return 'rgba(53,60,88,.1)';
                    } else if (number >= 5 && number < 30) {
                        return 'rgba(53,60,88,.2)';
                    } else if (number >= 30 && number < 60) {
                        return 'rgba(53,60,88,.3)';
                    }
                    return 'rgba(53,60,88,.4)';
                },

                /** Function to retrieve bounds of a specific sub-tile of a tile
                 *
                 * @param {L.Point} coords xyz coords of parent tile
                 * @param {number} rectIndex integer subtile index
                 * @returns {L.LatLngBounds} Bounds of specified sub-tile
                 */
                getRectBounds: function (coords, rectIndex) {
                    switch (rectIndex) {
                    case 0:
                        let subCoords0 = new L.Point(coords.x * 2, coords.y * 2);
                        subCoords0.z = coords.z + 1;
                        return this._tileCoordsToBounds(subCoords0);
                    case 1:
                        let subCoords1 = new L.Point(coords.x * 2 + 1, coords.y * 2);
                        subCoords1.z = coords.z + 1;
                        return this._tileCoordsToBounds(subCoords1);
                    case 2:
                        let subCoords2 = new L.Point(coords.x * 2 + 1, coords.y * 2 + 1);
                        subCoords2.z = coords.z + 1;
                        return this._tileCoordsToBounds(subCoords2);
                    case 3:
                        let subCoords3 = new L.Point(coords.x * 2, coords.y * 2 + 1);
                        subCoords3.z = coords.z + 1;
                        return this._tileCoordsToBounds(subCoords3);
                    default:
                        return null;
                    }
                },

                /** Callback function that will return bounds of a clicked sub-tile
                 *  Intended to be re-implmented by consumer
                 *
                 * @param {L.LatLngBounds} bounds bounds passed in by click sub-tile
                 * @return {L.LatLngBounds} passed bounds
                 */
                onClick: function (bounds) {
                    return bounds;
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
                        fill: '#fff',
                        padding: 2,
                        visible: false,
                        listening: false
                    });

                    let textRect = new Konva.Rect({
                        x: 0,
                        y: 0,
                        fill: '#738FFC',
                        width: text.getWidth(),
                        height: text.getHeight(),
                        cornerRadius: 2,
                        visible: false,
                        listening: false
                    });

                    let layer = new Konva.Layer();

                    function writeMessage(number, x, y, show) {
                        text.setText(number);
                        text.setAttrs({x: x - text.getWidth() / 2,
                                       y: y - text.getHeight() / 2,
                                       visible: show});
                        textRect.setAttrs({x: x - text.getWidth() / 2,
                                           y: y - text.getHeight() / 2,
                                           width: text.getWidth(),
                                           visible: show});
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
                    layer.add(textRect);
                    layer.add(text);

                    this.requestGrid(coords, this.params).then((result) => {
                        let data = result.data;
                        topLeft.on('click', (e) => {
                            const bounds = this.getRectBounds(coords, 0);
                            this.onClick(e, bounds);
                        });
                        topLeft.on('mouseover', function () {
                            writeMessage(data[0].toString(), 64, 64, true);
                        });
                        topLeft.on('mouseout', function () {
                            writeMessage('', 64, 64, false);
                        });
                        topLeft.fill(this.getColor(data[0]));

                        topRight.on('click', (e) => {
                            const bounds = this.getRectBounds(coords, 1);
                            this.onClick(e, bounds);
                        });
                        topRight.on('mouseover', function () {
                            writeMessage(data[1].toString(), 192, 64, true);
                        });
                        topRight.on('mouseout', function () {
                            writeMessage('', 192, 64, false);
                        });
                        topRight.fill(this.getColor(data[1]));

                        bottomLeft.on('click', (e) => {
                            const bounds = this.getRectBounds(coords, 3);
                            this.onClick(e, bounds);
                        });
                        bottomLeft.on('mouseover', function () {
                            writeMessage(data[3].toString(), 64, 192, true);
                        });
                        bottomLeft.on('mouseout', function () {
                            writeMessage('', 64, 192, false);
                        });
                        bottomLeft.fill(this.getColor(data[3]));

                        bottomRight.on('click', (e) => {
                            const bounds = this.getRectBounds(coords, 2);
                            this.onClick(e, bounds);
                        });
                        bottomRight.on('mouseover', function () {
                            writeMessage(data[2].toString(), 192, 192, true);
                        });
                        bottomRight.on('mouseout', function () {
                            writeMessage('', 192, 192, false);
                        });
                        bottomRight.fill(this.getColor(data[2]));

                        stage.add(layer);
                        done(null, tile);
                    });
                    tile.konvaContexts = [
                        stage, text, textRect, layer, topLeft, topRight,
                        bottomLeft, bottomRight
                    ];

                    return tile;
                }
            });

            let gridLayer = new GridLayer();
            gridLayer.on('tileunload', (event) => {
                event.tile.konvaContexts.forEach((context) => context.destroy());
            });

            return gridLayer;
        }
    }

    app.service('gridLayerService', GridLayerService);
};

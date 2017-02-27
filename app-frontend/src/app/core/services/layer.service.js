export default (app) => {
    /**
     * Represents a layer that can be added to the map
     * with various transformations
     */
    class Layer {

        /**
         * Creates a layer from a scene -- this may need to be expanded
         * @param {object} $http injected angular $http service
         * @param {object} $q promise service
         * @param {object} authService service for auth, used to get token for layers
         * @param {object} colorCorrectService color correction service
         * @param {object} projectService project service
         * @param {object} scene response from the API, optional
         * @param {object} projectId project that layer is in
         * @param {boolean} projectMosaic flag to enable requesting layers from mosaic tile server
         * @param {boolean} gammaCorrect flag to enable gamma correction
         * @param {boolean} sigmoidCorrect flag to enable sigmoidal correction
         * @param {boolean} colorClipCorrect flag to enable color clipping
         * @param {object} bands keys = band type, values = band number
         */
        constructor( // eslint-disable-line max-params
            $http, $q, authService, colorCorrectService, projectService, scene, projectId,
            projectMosaic = true, gammaCorrect = true, sigmoidCorrect = true,
            colorClipCorrect = true, bands = {red: 3, green: 2, blue: 1}
        ) {
            this.$http = $http;
            this.$q = $q;
            this.authService = authService;
            this.scene = scene;
            this.projectMosaic = projectMosaic;
            this.gammaCorrect = gammaCorrect;
            this.sigmoidCorrect = sigmoidCorrect;
            this.colorClipCorrect = colorClipCorrect;
            this.colorCorrectService = colorCorrectService;
            this.projectService = projectService;
            this.bands = bands;
            this.projectId = projectId;
            this._sceneTiles = null; // eslint-disable-line no-underscore-dangle
            this._mosaicTiles = null; // eslint-disable-line no-underscore-dangle
            this._correction = null; // eslint-disable-line no-underscore-dangle
        }

        /** Function to return bounds from either the project or the scene
          *
          * @return {object} Leaflet latLngBounds
          */
        getBounds() {
            if (this.projectMosaic) {
                this.projectService.getProjectCorners(this.projectId).then((data) => {
                    this.bounds = L.latLngBounds(
                        L.latLng(
                            data.lowerLeftLat,
                            data.lowerLeftLon
                        ),
                        L.latLng(
                            data.upperRightLat,
                            data.upperRightLon
                        )
                    );
                });
            } else {
                this.bounds = L.latLngBounds(
                    L.latLng(
                        this.scene.sceneMetadata.lowerLeftCornerLatitude,
                        this.scene.sceneMetadata.lowerLeftCornerLongitude
                    ),
                    L.latLng(
                        this.scene.sceneMetadata.upperRightCornerLatitude,
                        this.scene.sceneMetadata.upperRightCornerLongitude
                    )
                );
            }
        }

        /** Function to return a promise that resolves into a leaflet tile layer for scenes
         *
         * @return {$promise} promise for leaflet tile layer for scenes
         */
        getSceneTileLayer() {
            if (this._sceneTiles) { // eslint-disable-line no-underscore-dangle
                return this.$q((resolve) => {
                    resolve(this._sceneTiles); // eslint-disable-line no-underscore-dangle
                });
            }
            return this.getSceneLayerURL().then((url) => {
                this._sceneTiles = L.tileLayer(url, // eslint-disable-line no-underscore-dangle
                    {bounds: this.bounds, attribution: 'Raster Foundry'}
                );
                return this._sceneTiles; // eslint-disable-line no-underscore-dangle
            });
        }

        /** Function to return a promise that resolves into a leaflet tile layer for mosaic
         *
         * @return {$promise} promise for leaflet tile layer for mosaic
         */
        getMosaicTileLayer() {
            if (this._mosaicTiles) { // eslint-disable-line no-underscore-dangle
                return this.$q((resolve) => {
                    resolve(this._mosaicTiles); // eslint-disable-line no-underscore-dangle
                });
            }
            return this.getMosaicLayerURL().then((url) => {
                this._mosaicTiles = L.tileLayer(url, // eslint-disable-line no-underscore-dangle
                                                {bounds: this.bounds, attribution: 'Raster Foundry'}
                                               );
                return this._mosaicTiles; // eslint-disable-line no-underscore-dangle
            });
        }

        getNDVILayer(bands = [5, 4]) {
            if (this._tiles) { // eslint-disable-line no-underscore-dangle
                return this.$q((resolve) => {
                    resolve(this._tiles); // eslint-disable-line no-underscore-dangle
                });
            }
            // eslint-disable-next-line no-underscore-dangle
            this._tiles = L.tileLayer(this.getNDVIURL(bands),
                {bounds: this.bounds, attribution: 'Raster Foundry'}
            );
            return this._tiles; // eslint-disable-line no-underscore-dangle
        }

        /**
         * Helper function to return string for a tile layer
         * @returns {string} URL for this tile layer
         */
        getSceneLayerURL() {
            return this.formatColorParams().then((formattedParams) => {
                return `/tiles/${this.scene.id}/rgb/{z}/{x}/{y}/?${formattedParams}`;
            });
        }

        getMosaicLayerURL(params = {}) {
            params.token = this.authService.token();
            let formattedParams = L.Util.getParamString(params);
            return this.$q((resolve) => {
                resolve(`/tiles/${this.projectId}/{z}/{x}/{y}/${formattedParams}`);
            });
        }

        getNDVIURL(bands) {
            return `/tiles/${this.scene.id}/ndvi/{z}/{x}/{y}/?bands=${bands[0]},${bands[1]}`;
        }

        /**
         * Helper function to return histogram endpoint url for a tile layer
         * @returns {string} URL for the histogram
         */
        getHistogramURL() {
            return this.formatColorParams().then((formattedParams) => {
                return `/tiles/${this.scene.id}/rgb/histogram/?${formattedParams}`;
            });
        }

        /**
         * Helper function to fetch histogram data for a tile layer
         * @returns {Promise} which should be resolved with an array
         */
        fetchHistogramData() {
            return this.getHistogramURL().then((url) => {
                return this.$http.get(url);
            });
        }

        /**
         * Helper function to update tile layer with new bands
         * @param {object} bands bands to update layer with
         * @returns {null} null
         */
        updateBands(bands = {redBand: 3, greenBand: 2, blueBand: 1}) {
            return this.getColorCorrection().then((correction) => {
                this.updateColorCorrection(Object.assign(correction, bands));
            });
        }

        /**
         * Reset tile layer with default color corrections
         * @returns {null} null
         */
        resetTiles() {
            this._correction = this.colorCorrectService // eslint-disable-line no-underscore-dangle
                .getDefaultColorCorrection();
            return this.colorCorrectService.reset(this.scene.id, this.projectId)
                .then(() => this.colorCorrect());
        }

        formatColorParams() {
            return this.getColorCorrection().then((colorCorrection) => {
                colorCorrection.token = this.authService.token();
                let formattedParams = L.Util.getParamString(colorCorrection);
                return formattedParams;
            });
        }

        /**
         * Helper function to turn a correction object into usable params
         *
         * for some reason  getColorCorrection's returned object includes all
         * sorts of extra attributes like $promise that we don't want leaflet
         * to turn into query params, so we have to grab only the parts that
         * we want to get the request to work.
         *
         * @param {object} object containing color correction params
         * @returns {object} initial object filtered to legal parameters
         */
        paramsFromObject(object) {
            return {
                redBand: object.redBand,
                blueBand: object.blueBand,
                greenBand: object.greenBand,
                redGamma: object.redGamma,
                greenGamma: object.greenGamma,
                blueGamma: object.blueGamma,
                brightness: object.brightness,
                contrast: object.contrast,
                alpha: object.alpha,
                beta: object.beta,
                min: object.min,
                max: object.max,
                tag: object.tag ? object.tag : (new Date()).getTime()
            };
        }

        getColorCorrection() {
            return this.colorCorrectService.get(
                this.scene.id, this.projectId
            ).then((data) => {
                this._correction = data; // eslint-disable-line no-underscore-dangle
                return this._correction; // eslint-disable-line no-underscore-dangle
            });
        }

        getCachedColorCorrection() {
            return this._correction; // eslint-disable-line no-underscore-dangle
        }

        updateColorCorrection(corrections) {
            this._correction = corrections; // eslint-disable-line no-underscore-dangle
            return this.colorCorrectService.updateOrCreate(
                this.scene.id, this.projectId, corrections
            ).then(() => this.colorCorrect());
        }

        /**
         * Apply color corrections to tile layer and refresh layer
         * @param {object} corrections object with various parameters color correcting
         * @returns {null} null
         */
        colorCorrect() {
            this.getSceneTileLayer().then((tiles) => {
                this.getSceneLayerURL().then((url) => {
                    return tiles.setUrl(url);
                });
            });
        }

        /**
         * Helper function to get user params from scene or list of scenes
         * @param {object|object[]}scene scene or list of scenes to extract user params from
         * @returns {object} {userId: url-safe user id, organizationId: url-safe org id}
         */
        userParamsFromScene(scene) {
            // if we have one scene, make it into an array and grab the first element.
            // if we have several scenes, concat them all to the empty array and take the first
            let tmp = [].concat(scene)[0];
            return {
                // TODO: replace this once user IDs are URL safe ISSUE: 766
                userId: tmp.createdBy.replace('|', '_'),
                organizationId: tmp.organizationId
            };
        }
    }

    class LayerService {
        constructor($http, $q, authService, colorCorrectService, projectService) {
            'ngInject';
            this.$http = $http;
            this.$q = $q;
            this.authService = authService;
            this.colorCorrectService = colorCorrectService;
            this.projectService = projectService;
        }

        /**
         * Constructor for layer via a service
         * @param {object} scene resource returned via API
         * @param {string} projectId id for project scene belongs to
         * @param {boolean} projectMosaic flag to enable requesting layers from mosaic tile server
         * @returns {Layer} layer created
         */
        layerFromScene(scene, projectId, projectMosaic = false) {
            return new Layer(this.$http, this.$q, this.authService,
                this.colorCorrectService, this.projectService,
                scene, projectId, projectMosaic);
        }
    }

    app.service('layerService', LayerService);
};

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
         * @param {object} APP_CONFIG config of application
         * @param {object} scene response from the API, optional
         * @param {object} projectId project that layer is in
         * @param {boolean} projectMosaic flag to enable requesting layers from mosaic tile server
         * @param {boolean} gammaCorrect flag to enable gamma correction
         * @param {boolean} sigmoidCorrect flag to enable sigmoidal correction
         * @param {boolean} colorClipCorrect flag to enable color clipping
         * @param {object} bands keys = band type, values = band number
         */
        constructor( // eslint-disable-line max-params
            $http, $q, authService, colorCorrectService, projectService, APP_CONFIG,
            scene, projectId, projectMosaic = true, gammaCorrect = true, sigmoidCorrect = true,
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
            this._sceneTiles = null;
            this._mosaicTiles = null;

            this.tileServer = `${APP_CONFIG.tileServerLocation}`;
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

        /** Function to return a promise that resolves into a leaflet tile layer for mosaic
         *
         * @return {$promise} promise for leaflet tile layer for mosaic
         */
        getMosaicTileLayer() {
            if (this._mosaicTiles) {
                return this.$q((resolve) => {
                    resolve(this._mosaicTiles);
                });
            }
            return this.getMosaicLayerURL().then((url) => {
                let options = {bounds: this.bounds};
                this._mosaicTiles = L.tileLayer(url, options);
                return this._mosaicTiles;
            });
        }

        getMosaicLayerURL(params = {}) {
            params.token = this.authService.token();
            let formattedParams = L.Util.getParamString(params);
            return this.$q((resolve) => {
                resolve(`${this.tileServer}/${this.projectId}/{z}/{x}/{y}/${formattedParams}`);
            });
        }

        /**
         * Return this layer's color correction object
         *
         * @returns {Promise} Promise for result from color correction service
         */
        getColorCorrection() {
            return this.colorCorrectService.get(this.scene.id, this.projectId);
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
                userId: tmp.owner.replace('|', '_'),
                organizationId: tmp.organizationId
            };
        }
    }

    class LayerService {
        constructor($http, $q, authService, colorCorrectService, projectService, APP_CONFIG) {
            'ngInject';
            this.$http = $http;
            this.$q = $q;
            this.authService = authService;
            this.colorCorrectService = colorCorrectService;
            this.projectService = projectService;
            this.APP_CONFIG = APP_CONFIG;
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
                this.colorCorrectService, this.projectService, this.APP_CONFIG,
                scene, projectId, projectMosaic);
        }
    }

    app.service('layerService', LayerService);
};

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
         * @param {object} colorCorrectService color correction service
         * @param {object} scene response from the API
         * @param {object} projectId project that layer is in
         * @param {boolean} gammaCorrect flag to enable gamma correction
         * @param {boolean} sigmoidCorrect flag to enable sigmoidal correction
         * @param {boolean} colorClipCorrect flag to enable color clipping
         * @param {object} bands keys = band type, values = band number
         */
        constructor( // eslint-disable-line max-params
            $http, $q, colorCorrectService, scene, projectId, gammaCorrect = true,
            sigmoidCorrect = true, colorClipCorrect = true, bands = {red: 3, green: 2, blue: 1}
        ) {
            this.$http = $http;
            this.$q = $q;
            this.scene = scene;
            this.gammaCorrect = gammaCorrect;
            this.sigmoidCorrect = sigmoidCorrect;
            this.colorClipCorrect = colorClipCorrect;
            this.colorCorrectService = colorCorrectService;
            this.bands = bands;
            this.projectId = projectId;
            this._tiles = null; // eslint-disable-line no-underscore-dangle
            this._correction = null; // eslint-disable-line no-underscore-dangle
            this.bounds = L.latLngBounds(
                L.latLng(
                    scene.sceneMetadata.lowerLeftCornerLatitude,
                    scene.sceneMetadata.lowerLeftCornerLongitude
                ),
                L.latLng(
                    scene.sceneMetadata.upperRightCornerLatitude,
                    scene.sceneMetadata.upperRightCornerLongitude
                )
            );
        }

        /** Function to return a promise that resolves into a leaflet tile layer
         *
         * @return {$promise} promise for leaflet tile layer
         */
        getTileLayer() {
            if (this._tiles) { // eslint-disable-line no-underscore-dangle
                return this.$q((resolve) => {
                    resolve(this._tiles); // eslint-disable-line no-underscore-dangle
                });
            }
            return this.getLayerURL().then((url) => {
                this._tiles = L.tileLayer(url, // eslint-disable-line no-underscore-dangle
                    {bounds: this.bounds, attribution: 'Raster Foundry'}
                );
                return this._tiles; // eslint-disable-line no-underscore-dangle
            });
        }

        /**
         * Helper function to return string for a tile layer
         * @returns {string} URL for this tile layer
         */
        getLayerURL() {
            let organizationId = this.scene.organizationId;
            // TODO: replace this once user IDs are URL safe ISSUE: 766
            let userId = this.scene.createdBy.replace('|', '_');
            return this.formatColorParams().then((formattedParams) => {
                return `/tiles/${organizationId}/` +
                    `${userId}/${this.scene.id}/rgb/{z}/{x}/{y}/?${formattedParams}`;
            });
        }

        /**
         * Helper function to return histogram endpoint url for a tile layer
         * @returns {string} URL for the histogram
         */
        getHistogramURL() {
            let organizationId = this.scene.organizationId;
            // TODO: replace this once user IDs are URL safe ISSUE: 766

            let userId = this.scene.createdBy.replace('|', '_');
            return this.formatColorParams().then((formattedParams) => {
                return `/tiles/${organizationId}/` +
                    `${userId}/${this.scene.id}/rgb/histogram/?${formattedParams}`;
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
            this.getColorCorrection().then((correction) => {
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
            this.colorCorrectService.reset(this.scene.id, this.projectId)
                .then(() => this.colorCorrect());
        }

        formatColorParams() {
            return this.getColorCorrection().then((colorCorrection) => {
                let colorCorrectParams = `redBand=${colorCorrection.redBand}&` +
                    `greenBand=${colorCorrection.greenBand}&` +
                    `blueBand=${colorCorrection.blueBand}`;

                if (this.gammaCorrect) {
                    colorCorrectParams = `${colorCorrectParams}` +
                        `&redGamma=${colorCorrection.redGamma}` +
                        `&greenGamma=${colorCorrection.greenGamma}` +
                        `&blueGamma=${colorCorrection.blueGamma}`;
                }

                if (this.sigmoidCorrect) {
                    colorCorrectParams = `${colorCorrectParams}` +
                        `&alpha=${colorCorrection.alpha}` +
                        `&beta=${colorCorrection.beta}`;
                }

                if (this.colorClipCorrect) {
                    colorCorrectParams = `${colorCorrectParams}`
                        + `&min=${colorCorrection.min}`
                        + `&max=${colorCorrection.max}`;
                }

                colorCorrectParams = `${colorCorrectParams}` +
                    `&brightness=${colorCorrection.brightness}` +
                    `&contrast=${colorCorrection.contrast}`;

                return colorCorrectParams;
            });
        }

        getColorCorrection() {
            if (this._correction) { // eslint-disable-line no-underscore-dangle
                return this.$q((resolve) => {
                    resolve(this._correction); // eslint-disable-line no-underscore-dangle
                });
            }
            return this.colorCorrectService.get(
                this.scene.id, this.projectId
            ).then((data) => {
                this._correction = data; // eslint-disable-line no-underscore-dangle
                return this._correction; // eslint-disable-line no-underscore-dangle
            });
        }

        updateColorCorrection(corrections) {
            this._correction = corrections; // eslint-disable-line no-underscore-dangle
            this.colorCorrectService.updateOrCreate(
                this.scene.id, this.projectId, corrections
            ).then(() => this.colorCorrect());
        }

        /**
         * Apply color corrections to tile layer and refresh layer
         * @param {object} corrections object with various parameters color correcting
         * @returns {null} null
         */
        colorCorrect() {
            this.getTileLayer().then((tiles) => {
                this.getLayerURL().then((url) => {
                    return tiles.setUrl(url);
                });
            });
        }
    }

    class LayerService {
        constructor($http, $q, colorCorrectService) {
            'ngInject';
            this.$http = $http;
            this.$q = $q;
            this.colorCorrectService = colorCorrectService;
        }

        /**
         * Constructor for layer via a service
         * @param {object} scene resource returned via API
         * @param {string} projectId id for project scene belongs to
         * @returns {Layer} layer created
         */
        layerFromScene(scene, projectId) {
            return new Layer(this.$http, this.$q, this.colorCorrectService, scene, projectId);
        }
    }

    app.service('layerService', LayerService);
};

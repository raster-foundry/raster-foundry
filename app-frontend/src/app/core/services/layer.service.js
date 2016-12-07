export default (app) => {
    /**
     * Represents a layer that can be added to the map
     * with various transformations
     */
    class Layer {

        baseColorCorrection() {
            return {
                red: 0,
                green: 0,
                blue: 0,
                brightness: 0,
                contrast: 0,
                alpha: 0,
                beta: 0,
                min: 0,
                max: 20000
            };
        }

        /**
         * Creates a layer from a scene -- this may need to be expanded
         * @param {object} scene response from the API
         * @param {boolean} gammaCorrect flag to enable gamma correction
         * @param {boolean} sigmoidCorrect flag to enable sigmoidal correction
         * @param {boolean} colorClipCorrect flag to enable color clipping
         * @param {object} bands keys = band type, values = band number
         */
        constructor(
            scene, gammaCorrect = true, sigmoidCorrect = false,
            colorClipCorrect = true, bands = {red: 3, green: 2, blue: 1}
        ) {
            this.scene = scene;
            this.gammaCorrect = gammaCorrect;
            this.sigmoidCorrect = sigmoidCorrect;
            this.colorClipCorrect = colorClipCorrect;
            this.bands = bands;

            // Initial Color Correction Values
            let bounds = L.latLngBounds(
                L.latLng(
                    scene.sceneMetadata.lowerLeftCornerLatitude,
                    scene.sceneMetadata.lowerLeftCornerLongitude
                ),
                L.latLng(
                    scene.sceneMetadata.upperRightCornerLatitude,
                    scene.sceneMetadata.upperRightCornerLongitude
                )
            );
            this.tiles = L.tileLayer(
                this.getLayerURL(),
                {bounds: bounds, attribution: 'Raster Foundry'}
            );
        }

        /**
         * Helper function to return string for a tile layer
         * @returns {string} URL for this tile layer
         */
        getLayerURL() {
            let organizationId = this.scene.organizationId;
            // TODO: replace this once user IDs are URL safe ISSUE: 766
            let userId = this.scene.createdBy.replace('|', '_');
            return `/tiles/${organizationId}/` +
                `${userId}/${this.scene.id}/rgb/{z}/{x}/{y}/?${this.bandsToQueryString()}`;
        }

        /**
         * Helper function to add bands to query string for tile layer
         * @returns {string} formatted query string for bands in tile layer
         */
        bandsToQueryString() {
            return `redBand=${this.bands.red}&` +
                `greenBand=${this.bands.green}&` +
                `blueBand=${this.bands.blue}`;
        }

        /**
         * Helper function to update tile layer with new bands
         * @param {object} bands bands to update layer with
         * @returns {null} null
         */
        updateBands(bands = {red: 3, green: 2, blue: 1}) {
            this.bands = bands;
            this.resetTiles();
        }

        /**
         * Reset tile layer without any color corrections
         * @returns {null} null
         */
        resetTiles() {
            this.tiles.setUrl(this.getLayerURL());
        }

        formatColorParams() {
            let colorCorrectParams = '';

            if (this.gammaCorrect) {
                colorCorrectParams = `${colorCorrectParams}` +
                    `&redGamma=${this.colorCorrection.red}` +
                    `&greenGamma=${this.colorCorrection.green}` +
                    `&blueGamma=${this.colorCorrection.blue}`;
            }
            if (this.sigmoidCorrect) {
                colorCorrectParams = `${colorCorrectParams}` +
                    `&alpha=${this.colorCorrection.alpha}` +
                    `&beta=${this.colorCorrection.beta}`;
            }
            if (this.colorClipCorrect) {
                colorCorrectParams = `${colorCorrectParams}`
                    + `&min=${this.colorCorrection.min}`
                    + `&max=${this.colorCorrection.max}`;
            }
            colorCorrectParams = `${colorCorrectParams}` +
                `&brightness=${this.colorCorrection.brightness}` +
                `&contrast=${this.colorCorrection.contrast}`;
            return colorCorrectParams;
        }

        /**
         * Apply color corrections to tile layer and refresh layer
         * @param {object} corrections object with various parameters color correcting
         * @returns {null} null
         */
        colorCorrect(corrections) {
            this.colorCorrection = corrections;

            let formattedParams = this.formatColorParams();
            this.tiles.setUrl(`${this.getLayerURL()}&${formattedParams}`);
        }

    }

    class LayerService {

        /**
         * Constructor for layer via a service
         * @param {object} scene resource returned via API
         * @returns {Layer} layer created
         */
        layerFromScene(scene) {
            return new Layer(scene);
        }
    }

    app.service('layerService', LayerService);
};

export default class ColorCorrectPaneController {
    constructor( // eslint-disable-line max-params
        $log, $scope, $q, projectService, $state
    ) {
        'ngInject';
        this.bucketService = projectService;
        this.$state = $state;
        this.$q = $q;
    }

    $onInit() {
        // If the user navigates directly here we don't know what scenes to
        // adjust so redirect to the scenes list.
        if (this.selectedLayers.size === 0) {
            this.$state.go('^.scenes');
            return;
        }
        // Initialize correction to first selected layer (if there are multiple)
        this.correction = this.selectedLayers.values().next().value.baseColorCorrection();

        // Fake data for our histogram; this will get replaced by a service call later.
        this.red = [];
        this.green = [];
        this.blue = [];
        for (let i of [0, 100, 200, 300, 400]) {
            this.red.push({x: i, y: i});
            this.green.push({x: i, y: 400 - i});
            this.blue.push({x: i, y: i / 2.0});
        }
        this.data = [
            {
                values: this.red,
                key: 'Red channel',
                color: '#bb0000',
                area: true
            },
            {
                values: this.green,
                key: 'Green channel',
                color: '#00bb00',
                area: true
            },
            {
                values: this.blue,
                key: 'Blue channel',
                color: '#0000dd',
                area: true
            }
        ];
    }

    resetCorrection() {
        for (let layer of this.selectedLayers.values()) {
            layer.resetTiles();
        }
        this.correction = this.selectedLayers.values().next().value.baseColorCorrection();
    }

    /**
     * Triggered when the adjustment pane reports changes to color correction
     *
     * Applies color corrections to all selected layers
     * @param {object} newCorrection object to apply to each layer
     *
     * @returns {null} null
     */
    onCorrectionChange(newCorrection) {
        this.correction = newCorrection;
        for (let layer of this.selectedLayers.values()) {
            layer.colorCorrect(this.correction);
        }
    }
}

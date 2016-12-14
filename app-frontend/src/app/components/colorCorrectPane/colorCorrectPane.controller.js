export default class ColorCorrectPaneController {
    constructor( // eslint-disable-line max-params
        $log, $scope, $q, projectService, $state
    ) {
        'ngInject';
        this.$parent = $scope.$parent.$ctrl;
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
        this.$parent.fitSelectedScenes();
        this.$parent.bringSelectedScenesToFront();
        // Initialize correction to first selected layer (if there are multiple)
        this.firstLayer = this.selectedLayers.values().next().value;
        this.correction = this.firstLayer.baseColorCorrection();
        this.updateHistogram();
    }

    $onDestroy() {
        this.$parent.fitAllScenes();
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
        if (newCorrection) {
            this.correction = newCorrection;
            for (let layer of this.selectedLayers.values()) {
                layer.colorCorrect(this.correction);
            }
            this.updateHistogram();
        }
    }

    updateHistogram() {
        this.firstLayer.fetchHistogramData().then(
            (resp) => {
                this.errorLoadingHistogram = false;
                this.data = this.generateHistogramData(resp.data);
            }
        ).catch(() => {
            this.errorLoadingHistogram = true;
        });
    }

    generateHistogramData(data) {
        return [
            {
                values: data[0].map(([x, y]) => ({x, y})),
                key: 'Red channel',
                color: '#bb0000',
                area: true
            },
            {
                values: data[1].map(([x, y]) => ({x, y})),
                key: 'Green channel',
                color: '#00bb00',
                area: true
            },
            {
                values: data[2].map(([x, y]) => ({x, y})),
                key: 'Blue channel',
                color: '#0000dd',
                area: true
            }
        ];
    }
}

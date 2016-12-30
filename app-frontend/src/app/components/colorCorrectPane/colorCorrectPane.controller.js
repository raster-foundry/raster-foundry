export default class ColorCorrectPaneController {
    constructor( // eslint-disable-line max-params
        $log, $scope, $q, projectService, $state
    ) {
        'ngInject';
        this.$parent = $scope.$parent.$ctrl;
        this.projectService = projectService;
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
        this.smoothHistograms = true;
        // Initialize correction to first selected layer (if there are multiple)
        this.firstLayer = this.selectedLayers.values().next().value;
        this.correction = this.firstLayer.baseColorCorrection();
        this.fetchHistograms();
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
            this.fetchHistograms();
        }
    }

    addDataToHistogram(data) {
        data.forEach((channel, channelIndex) => {
            for (let bin of channel) {
                // eslint-disable-next-line operator-assignment
                this.histogramRawData[channelIndex][bin[0] - 1][1] += bin[1];
            }
        });
        this.updateHistogram();
    }

    fetchHistograms() {
        this.histogramRawData = this.generateBaseHistogramData();
        this.selectedLayers.forEach(l => {
            l.fetchHistogramData().then(
                (resp) => {
                    this.addDataToHistogram(resp.data);
                    this.updateHistogram();
                }
            ).catch(() => {
                this.errorLoadingHistogram = true;
            });
        });
    }

    updateHistogram() {
        let data = this.histogramRawData;
        if (this.smoothHistograms) {
            data = this.histogramRawData.map((c) => {
                return c.filter(b => b[1] > 0);
            });
        }
        this.data = this.generateHistogramData(data);
    }

    generateBaseHistogramData() {
        return [
            new Array(255).fill().map((x, i) => [i, 0]),
            new Array(255).fill().map((x, i) => [i, 0]),
            new Array(255).fill().map((x, i) => [i, 0])
        ];
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

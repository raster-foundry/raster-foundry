export default class ColorCorrectPaneController {
    constructor( // eslint-disable-line max-params
        $log, $scope, $q, projectService, $state, featureFlags,
        sceneService, mapService, colorCorrectService
    ) {
        'ngInject';
        this.$parent = $scope.$parent.$ctrl;
        this.projectService = projectService;
        this.sceneService = sceneService;
        this.featureFlags = featureFlags;
        this.colorCorrectService = colorCorrectService;
        this.$state = $state;
        this.$q = $q;
        this.getMap = () => mapService.getMap('project');
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
        this.firstLayer.getColorCorrection().then(this.setCorrection.bind(this));
        this.mosaic = this.$parent.mosaicLayer.values().next().value;
        if (this.featureFlags.isOn('display-histogram')) {
            this.fetchHistograms();
        }
        this.getMap().then((map) => {
            this.selectedScenes.forEach((scene) => {
                map.setGeojson(scene.id, this.sceneService.getStyledFootprint(scene));
            });
        });
    }

    $onDestroy() {
        this.$parent.fitProjectExtent();
        this.getMap().then((map) => {
            this.selectedScenes.forEach((scene) => map.deleteGeojson(scene.id));
        });
    }

    resetCorrection() {
        const sceneIds = Array.from(this.selectedScenes.keys());
        const promise = this.colorCorrectService.bulkUpdate(
            this.projectService.currentProject.id,
            sceneIds
        );
        const defaultCorrection = this.colorCorrectService.getDefaultColorCorrection();

        if (this.featureFlags.isOn('display-histogram')) {
            this.fetchHistograms();
        }
        this.setCorrection(defaultCorrection);
        this.redrawMosaic(promise, defaultCorrection);
    }

    setCorrection(correction) {
        this.correction = correction;
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
        const sceneIds = Array.from(this.selectedScenes.keys());
        if (newCorrection) {
            const promise = this.colorCorrectService.bulkUpdate(
                this.projectService.currentProject.id,
                sceneIds,
                newCorrection
            );

            if (this.featureFlags.isOn('display-histogram')) {
                this.fetchHistograms();
            }
            this.redrawMosaic(promise, newCorrection);
        }
    }

    redrawMosaic(promise, newCorrection) {
        promise.then(() => {
            this.mosaic.getMosaicTileLayer().then((tiles) => {
                let newParams = this.mosaic.paramsFromObject(newCorrection);
                this.mosaic.getMosaicLayerURL(newParams).then((url) => {
                    tiles.setUrl(url);
                });
            });
        });
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

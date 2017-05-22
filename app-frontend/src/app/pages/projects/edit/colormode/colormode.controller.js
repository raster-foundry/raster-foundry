export default class ProjectsEditColormode {
    constructor($scope, $q, colorCorrectService) {
        'ngInject';
        this.$scope = $scope;
        this.$parent = $scope.$parent.$ctrl;
        this.$q = $q;
        this.colorCorrectService = colorCorrectService;

        this.bands = {
            natural: {
                label: 'Natural Color',
                value: {redBand: 3, greenBand: 2, blueBand: 1}
            },
            cir: {
                label: 'Color infrared',
                value: {redBand: 4, greenBand: 3, blueBand: 2}
            },
            urban: {
                label: 'Urban',
                value: {redBand: 6, greenBand: 5, blueBand: 4}
            },
            water: {
                label: 'Water',
                value: {redBand: 4, greenBand: 5, blueBand: 3}
            },
            atmosphere: {
                label: 'Atmosphere penetration',
                value: {redBand: 6, greenBand: 4, blueBand: 2}
            },
            agriculture: {
                label: 'Agriculture',
                value: {redBand: 5, greenBand: 4, blueBand: 1}
            },
            forestfire: {
                label: 'Forest Fire',
                value: {redBand: 6, greenBand: 4, blueBand: 1}
            },
            bareearth: {
                label: 'Bare Earth change detection',
                value: {redBand: 5, greenBand: 2, blueBand: 1}
            },
            vegwater: {
                label: 'Vegetation & water',
                value: {redBand: 4, greenBand: 6, blueBand: 0}
            }
        };
    }

    isActiveColorMode(key) {
        let layer = this.$parent.sceneLayers.values().next();
        let currentBands = {};
        if (layer && layer.value) {
            currentBands = layer.value.getCachedColorCorrection();
        }
        let keyBands = this.bands[key].value;

        let isActive = currentBands &&
            keyBands.redBand === currentBands.redBand &&
            keyBands.greenBand === currentBands.greenBand &&
            keyBands.blueBand === currentBands.blueBand;
        return isActive;
    }

    setBands(bandName) {
        this.mosaic = this.$parent.mosaicLayer.values().next().value;
        let promises = [];
        this.$parent.sceneLayers.forEach((layer) => {
            promises.push(layer.updateBands(this.$parent.unifiedComposites[bandName].value));
        });
        this.redrawMosaic(promises, this.$parent.unifiedComposites[bandName].value);
    }

    /**
     * Trigger the redraw of the mosaic layer with new bands
     *
     * @param {promise[]} promises array of scene color correction promises
     * @param {object} newBands new mapping of band numbers
     * @returns {null} null
     */
    redrawMosaic(promises, newBands) {
        if (!promises.length) {
            return;
        }
        this.mosaic.getColorCorrection().then((lastCorrection) => {
            let ccParams = this.mosaic.paramsFromObject(lastCorrection);
            return Object.assign(ccParams, newBands);
        }).then((newCorrection) => {
            this.$q.all(promises).then(() => {
                this.mosaic.getMosaicTileLayer().then((tiles) => {
                    // eslint-disable-next-line max-nested-callbacks
                    this.mosaic.getMosaicLayerURL(newCorrection).then((url) => {
                        tiles.setUrl(url);
                    });
                });
                this.colorCorrectService.bulkUpdate(
                    this.$parent.project.id,
                    Array.from(this.$parent.sceneList.map((scene) => scene.id)),
                    newCorrection
                );
            });
        });
    }
}

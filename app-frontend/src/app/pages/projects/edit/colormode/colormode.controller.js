export default class ProjectsEditColormode {
    constructor($scope, $q, colorCorrectService, projectService) {
        'ngInject';
        this.$scope = $scope;
        this.$parent = $scope.$parent.$ctrl;
        this.$q = $q;
        this.colorCorrectService = colorCorrectService;
        this.projectService = projectService;

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

        this.currentBands = null;
        this.correction = null;
    }

    $onInit() {
        this.$parent.sceneListQuery.then(() => {
            let layer = this.$parent.sceneLayers.values().next();
            if (layer && layer.value) {
                layer.value.getColorCorrection().then((correction) => {
                    this.currentBands = {
                        redBand: correction.redBand,
                        greenBand: correction.greenBand,
                        blueBand: correction.blueBand
                    };
                    this.correction = correction;
                });
            }
        });
    }

    isActiveColorMode(key) {
        let keyBands = this.bands[key].value;

        let isActive = this.currentBands &&
            keyBands.redBand === this.currentBands.redBand &&
            keyBands.greenBand === this.currentBands.greenBand &&
            keyBands.blueBand === this.currentBands.blueBand;
        return isActive;
    }

    setBands(bandName) {
        this.currentBands = this.bands[bandName].value;
        this.correction = Object.assign(this.correction, this.currentBands);
        const promise = this.colorCorrectService.bulkUpdate(
            this.projectService.currentProject.id,
            Array.from(this.$parent.sceneLayers.keys()),
            this.correction
        );
        this.redrawMosaic(promise);
    }

    /**
     * Trigger the redraw of the mosaic layer with new bands
     *
     * @param {promise[]} promises array of scene color correction promises
     * @returns {null} null
     */
    redrawMosaic(promises) {
        this.$q.all(promises).then(() => {
            this.$parent.layerFromProject();
        });
    }
}

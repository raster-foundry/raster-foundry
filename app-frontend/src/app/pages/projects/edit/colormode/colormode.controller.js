const availableBands = [
    {
        label: 'Band 0',
        value: 0
    }, {
        label: 'Band 1',
        value: 1
    }, {
        label: 'Band 2',
        value: 2
    }, {
        label: 'Band 3',
        value: 3
    }, {
        label: 'Band 4',
        value: 4
    }, {
        label: 'Band 5',
        value: 5
    }, {
        label: 'Band 6',
        value: 6
    }, {
        label: 'Band 7',
        value: 7
    }, {
        label: 'Band 8',
        value: 8
    }, {
        label: 'Band 9',
        value: 9
    }, {
        label: 'Band 10',
        value: 10
    }, {
        label: 'Band 11',
        value: 11
    }
];

export default class ProjectsEditColormode {
    constructor($scope, colorCorrectService, projectService) {
        'ngInject';
        this.$scope = $scope;
        this.$parent = $scope.$parent.$ctrl;
        this.colorCorrectService = colorCorrectService;
        this.projectService = projectService;
    }

    $onInit() {
        this.isLoading = true;
        this.availableBands = availableBands;
        this.currentBands = null;
        this.correction = null;
        this.defaultColorModes = {
            custom: {
                label: 'Custom',
                value: {
                    mode: 'custom-rgb',
                    blueBand: 1,
                    redBand: 3,
                    greenBand: 2
                }
            },
            singleband: {
                label: 'Single Band',
                value: {
                    mode: 'single',
                    band: 0
                }
            }
        };
        this.initSceneLayers();
    }

    initSceneLayers() {
        const sceneListQuery = this.$parent.sceneListQuery || this.$parent.getSceneList();
        return sceneListQuery.then(() => {
            let layer = this.$parent.sceneLayers.values().next();
            if (layer && layer.value) {
                layer.value.getColorCorrection().then((correction) => {
                    this.currentBands = {
                        redBand: correction.redBand,
                        greenBand: correction.greenBand,
                        blueBand: correction.blueBand
                    };
                    this.correction = correction;
                    if (!this.correction.mode) {
                        this.correction.mode = 'multi';
                    }
                    this.$parent.fetchUnifiedComposites().then(() => {
                        this.unifiedComposites =
                            Object.assign(
                                {},
                                this.$parent.unifiedComposites,
                                this.defaultColorModes
                            );
                        this.activeColorModeKey = this.initActiveColorMode();
                        this.isLoading = false;
                    });
                });
            }
        });
    }

    initActiveColorMode() {
        const key = Object.keys(this.unifiedComposites).find(k => {
            const c = this.unifiedComposites[k].value;

            if (!c.mode) {
                return this.correction.redBand === c.redBand &&
                    this.correction.greenBand === c.greenBand &&
                    this.correction.blueBand === c.blueBand;
            }

            return this.correction.mode === c.mode &&
                this.correction.redBand === c.redBand &&
                this.correction.greenBand === c.greenBand &&
                this.correction.blueband === c.blueBand;
        });

        if (!key) {
            this.initCustomCorrection();
            return 'custom';
        }

        return key;
    }

    initCustomCorrection() {
        this.unifiedComposites.custom.value.redBand = this.correction.redBand;
        this.unifiedComposites.custom.value.greenBand = this.correction.greenBand;
        this.unifiedComposites.custom.value.blueBand = this.correction.blueBand;
        this.correction.mode = 'custom-rgb';
    }

    getActiveColorMode() {
        return this.unifiedComposites[this.activeColorModeKey];
    }


    setActiveColorMode(key, save = true) {
        this.activeColorModeKey = key;
        this.correction = Object.assign({}, this.correction, this.getActiveColorMode().value);
        if (save) {
            this.saveCorrection();
        }
    }

    isActiveColorMode(key) {
        return key === this.activeColorModeKey;
    }

    getActiveBand(bandName) {
        return this.getActiveColorMode().value[bandName];
    }

    setActiveBand(bandName, bandValue, save = true) {
        this.correction[bandName] = bandValue;
        this.unifiedComposites.custom.value[bandName] = bandValue;
        if (save) {
            this.saveCorrection();
        }
    }

    saveCorrection() {
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
     * @param {promise} promise color-correction promise
     * @returns {null} null
     */
    redrawMosaic(promise) {
        promise.then(() => {
            this.$parent.layerFromProject();
        });
    }
}

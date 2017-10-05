/* global _ */

const availableBands = require('./bands.json');

export default class ProjectsEditColormode {

    constructor(
        $scope, colorCorrectService, colorSchemeService, projectService, projectEditService
    ) {
        'ngInject';
        this.$scope = $scope;
        this.$parent = $scope.$parent.$ctrl;
        this.colorCorrectService = colorCorrectService;
        this.colorSchemeService = colorSchemeService;
        this.projectService = projectService;
        this.projectEditService = projectEditService;
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
                        this.initProjectBuffer();
                        this.isLoading = false;
                        this.activeColorModeKey = this.initActiveColorMode();
                    });
                });
            }
        });
    }

    initProjectBuffer() {
        this.initSingleBandDefaults();
        this.projectBuffer = Object.assign({}, this.$parent.project);
        if (this.projectBuffer.isSingleBand) {
            this.initActiveScheme();
        }
    }

    /**
     * Single-band functions
     */

    initActiveScheme() {
        this.projectBuffer.singleBandOptions =
            Object.assign({}, this.defaultSingleBandOptions, this.projectBuffer.singleBandOptions);

        this.activeColorSchemeType =
            this.colorSchemeService.defaultColorSchemeTypes.find(
                t => t.value === this.projectBuffer.singleBandOptions.dataType
            );

        this.activeColorScheme = this.colorSchemeService.matchSingleBandOptions(
            this.projectBuffer.singleBandOptions
        );

        this.activeColorBlendMode =
            this.colorSchemeService.defaultColorBlendModes.find(
                m => m.value === this.projectBuffer.singleBandOptions.colorBins
            );
    }

    initSingleBandDefaults() {
        const scheme = this.activeColorScheme || this.colorSchemeService.defaultColorSchemes[0];
        this.defaultSingleBandOptions = {
            band: 0,
            dataType: scheme.type,
            colorScheme: this.colorSchemeService.colorStopsToProportionalArray(scheme.colors),
            colorBins: 0,
            legendOrientation: 'left'
        };
    }

    getSerializedSingleBandOptions() {
        return angular.toJson(this.projectBuffer.singleBandOptions);
    }

    toggleProjectSingleBandMode(state) {
        this.initSingleBandDefaults();
        if (typeof state !== 'undefined') {
            this.projectBuffer.isSingleBand = state;
        } else {
            this.projectBuffer.isSingleBand = !this.projectBuffer.isSingleBand;
        }
        this.initActiveScheme();
        this.updateProjectFromBuffer();
    }

    getActiveBand(bandName) {
        return this.getActiveColorMode().value[bandName];
    }

    setActiveSingleBand(bandValue) {
        this.projectBuffer.singleBandOptions.band = bandValue;
        this.updateProjectFromBuffer();
    }

    getActiveSingleBand() {
        return this.projectBuffer.singleBandOptions.band;
    }

    getActiveColorScheme() {
        return this.activeColorScheme;
    }

    isActiveColorScheme(scheme) {
        return this.activeColorScheme.label === scheme.label && _.isEqual(
            this.activeColorScheme.colors,
            scheme.colors
        );
    }

    getFullyQualifiedColorScheme() {
        return this.colorSchemeService.colorsToDiscreteScheme(this.activeColorScheme.colors);
    }

    setActiveColorScheme(scheme, save = false) {
        if (!this.isLoading) {
            this.activeColorScheme = scheme;
            this.activeColorSchemeType =
                this.colorSchemeService.defaultColorSchemeTypes.find(t => t.value === scheme.type);
            this.projectBuffer.singleBandOptions.dataType = scheme.type;
            if (scheme.type !== 'CATEGORICAL') {
                this.projectBuffer.singleBandOptions.colorScheme =
                    this.colorSchemeService.colorStopsToProportionalArray(
                        this.activeColorScheme.colors
                    );
            } else if (scheme.breaks) {
                this.projectBuffer.singleBandOptions.colorScheme =
                    this.colorSchemeService
                        .schemeFromBreaksAndColors(
                            this.activeColorScheme.breaks,
                            this.activeColorScheme.colors
                        );
            } else {
                this.projectBuffer.singleBandOptions.colorScheme =
                    this.colorSchemeService.colorsToSequentialScheme(
                        this.colorSchemeService.colorStopsToProportionalArray(
                            this.activeColorScheme.colors
                        )
                    );
            }
            if (save) {
                this.updateProjectFromBuffer();
            }
        }
    }

    shouldShowColorScheme() {
        return (
            this.projectBuffer.singleBandOptions &&
                this.projectBuffer.singleBandOptions.dataType === 'SEQUENTIAL' ||
                this.projectBuffer.singleBandOptions.dataType === 'DIVERGING'
        );
    }

    shouldShowColorSchemeBuilder() {
        return this.projectBuffer.singleBandOptions.dataType === 'CATEGORICAL';
    }

    shouldShowBlendMode() {
        return this.projectBuffer.singleBandOptions.dataType !== 'CATEGORICAL';
    }

    updateProjectFromBuffer() {
        this.projectEditService.updateCurrentProject(this.projectBuffer).then(() => {
            this.$parent.project = this.projectBuffer;
            this.redrawMosaic();
        });
    }

    onSchemeColorsChange(schemeColors) {
        const breaks = Object.keys(schemeColors);
        const colors = breaks.map(b => schemeColors[b]);

        this.setActiveColorScheme({
            label: 'Custom categorical scheme',
            mappedScheme: schemeColors,
            colors: colors,
            breaks: breaks,
            type: 'CATEGORICAL'
        }, true);
    }

    /**
     * RGB-composite functions
     */

    initActiveColorMode() {
        const key = Object.keys(this.unifiedComposites).find(k => {
            const c = this.unifiedComposites[k].value;

            return (
                this.correction.redBand === c.redBand &&
                this.correction.greenBand === c.greenBand &&
                this.correction.blueBand === c.blueBand
            );
        });

        if (!key) {
            this.initCustomColorMode();
            return 'custom';
        }

        return key;
    }

    initCustomColorMode() {
        this.unifiedComposites.custom.value.redBand = this.correction.redBand;
        this.unifiedComposites.custom.value.greenBand = this.correction.greenBand;
        this.unifiedComposites.custom.value.blueBand = this.correction.blueBand;
        this.correction.mode = 'custom-rgb';
    }

    getActiveColorMode() {
        return this.unifiedComposites[this.activeColorModeKey];
    }

    setActiveColorMode(key, save = true) {
        if (key === 'singleband') {
            this.toggleProjectSingleBandMode(true);
        } else {
            this.toggleProjectSingleBandMode(false);
            this.activeColorModeKey = key;
            this.correction = Object.assign({}, this.correction, this.getActiveColorMode().value);
            if (save) {
                this.saveCorrection();
            }
        }
    }

    isActiveColorMode(key) {
        if (!this.isLoading) {
            return (
                !this.projectBuffer.isSingleBand &&
                key === this.activeColorModeKey
            );
        }
        return false;
    }

    setActiveBand(bandName, bandValue, save = true) {
        this.correction[bandName] = bandValue;

        if (this.activeColorModeKey === 'custom') {
            this.unifiedComposites.custom.value[bandName] = bandValue;
        } else if (this.activeColorModeKey === 'singleband') {
            this.unifiedComposites.singleband.value[bandName] = bandValue;
        }

        if (save) {
            this.saveCorrection();
        }
    }

    saveCorrection() {
        this.colorCorrectService.bulkUpdate(
            this.projectEditService.currentProject.id,
            Array.from(this.$parent.sceneLayers.keys()),
            this.correction
        ).then(() => {
            this.redrawMosaic();
        });
    }

    /**
     * Trigger the redraw of the mosaic layer with new bands
     *
     * @returns {null} null
     */
    redrawMosaic() {
        this.$parent.layerFromProject();
    }

    onColorSchemeChange(colorSchemeOptions) {
        let oldOptions = this.projectBuffer.singleBandOptions;
        this.activeColorSchemeType = oldOptions.dataType;
        if (
            JSON.stringify(colorSchemeOptions.colorScheme) !==
                JSON.stringify(oldOptions.colorScheme)
        ) {
            this.activeColorSchemeType = colorSchemeOptions.dataType;
            this.projectBuffer.singleBandOptions = Object.assign(
                {},
                this.projectBuffer.singleBandOptions,
                colorSchemeOptions
            );
            this.updateProjectFromBuffer();
        }
    }
}

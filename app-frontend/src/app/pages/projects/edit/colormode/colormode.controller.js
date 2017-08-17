/* global _ */

const availableBands = require('./bands.json');

export default class ProjectsEditColormode {

    constructor($scope, colorCorrectService, colorSchemeService, projectService) {
        'ngInject';
        this.$scope = $scope;
        this.$parent = $scope.$parent.$ctrl;
        this.colorCorrectService = colorCorrectService;
        this.colorSchemeService = colorSchemeService;
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

        this.activeColorScheme =
            this.colorSchemeService.defaultColorSchemes.find(
                s => _.isEqual(
                    this.projectBuffer.singleBandOptions.colorScheme,
                    this.colorSchemeService.colorsToDiscreteScheme(s.colors)
                )
            );

        this.activeColorBlendMode =
            this.colorSchemeService.defaultColorBlendModes.find(
                m => m.value === this.projectBuffer.singleBandOptions.blendMode
            );
    }

    initSingleBandDefaults() {
        const scheme = this.activeColorScheme || this.colorSchemeService.defaultColorSchemes[0];
        this.defaultSingleBandOptions = {
            band: 0,
            dataType: scheme.type,
            colorScheme: this.colorSchemeService.colorsToDiscreteScheme(scheme.colors),
            blendMode: 'CONTINUOUS',
            legendOrientation: 'left'
        };
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
                    this.colorSchemeService.colorsToDiscreteScheme(this.activeColorScheme.colors);
            } else if (scheme.breaks) {
                this.projectBuffer.singleBandOptions.colorScheme =
                    this.colorSchemeService
                        .schemeFromBreaksAndColors(
                            this.activeColorScheme.breaks,
                            this.activeColorScheme.colors
                        );
            } else {
                this.projectBuffer.singleBandOptions.colorScheme =
                    this.colorSchemeService.colorsToSequentialScheme(this.activeColorScheme.colors);
            }
            if (save) {
                this.updateProjectFromBuffer();
            }
        }
    }

    setActiveColorSchemeType(type) {
        if (this.activeColorSchemeType.value !== type.value) {
            this.activeColorSchemeType = type;
            const firstSchemeOfType = this.colorSchemeService.defaultColorSchemes.find(
                s => s.type === type.value
            );
            this.setActiveColorScheme(firstSchemeOfType, true);
        }
    }

    getActiveColorSchemeType() {
        if (!this.isLoading) {
            return this.activeColorSchemeType;
        }
        return null;
    }

    setActiveColorBlendMode(blendMode) {
        if (this.activeColorBlendMode.value !== blendMode.value) {
            this.activeColorBlendMode = blendMode;
            this.projectBuffer.singleBandOptions.blendMode = this.activeColorBlendMode.value;
            this.updateProjectFromBuffer();
        }
    }

    getActiveColorBlendMode() {
        if (!this.isLoading) {
            return this.activeColorBlendMode;
        }
        return null;
    }

    shouldShowColorScheme() {
        return (
            this.activeColorSchemeType && (
                this.activeColorSchemeType.value === 'SEQUENTIAL' ||
                this.activeColorSchemeType.value === 'DIVERGING'
            )
        );
    }

    shouldShowColorSchemeBuilder() {
        return this.activeColorSchemeType.value === 'CATEGORICAL';
    }

    shouldShowBlendMode() {
        return this.activeColorSchemeType.value !== 'CATEGORICAL';
    }

    updateProjectFromBuffer() {
        this.projectService.updateProject(this.projectBuffer).then(() => {
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
            this.projectService.currentProject.id,
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
}



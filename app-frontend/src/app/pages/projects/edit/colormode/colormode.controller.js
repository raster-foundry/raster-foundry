/* global _ */

export default class ProjectsEditColormode {
    constructor(
        $scope, $q, $state,
        colorCorrectService, colorSchemeService, projectService, projectEditService
    ) {
        'ngInject';
        $scope.autoInject(this, arguments);
        this.$parent = $scope.$parent.$ctrl;
    }

    $onInit() {
        this.isLoading = true;
        this.currentBands = null;
        this.defaultColorModes = {
            custom: {
                label: 'Custom',
                value: {
                    mode: 'custom-rgb',
                    blueBand: 1,
                    greenBand: 2,
                    redBand: 3
                }
            }
        };
        this.initColorModes();
    }

    initColorModes() {
        let sceneListQuery;
        if (!this.$parent.sceneList) {
            sceneListQuery = this.$parent.currentQuery || this.$parent.fetchPage();
        } else {
            sceneListQuery = this.$q.resolve(this.$parent.sceneList);
        }
        sceneListQuery.then(() => {
            let projectScene = _.first(this.$parent.sceneList);
            if (projectScene) {
                return this.colorCorrectService.get(projectScene.id, this.$parent.projectId);
            }
            return this.$q.resolve();
        }).then((correction) => {
            if (!correction) {
                this.isLoading = false;
                return;
            }
            this.currentBands = {
                redBand: correction.redBand,
                greenBand: correction.greenBand,
                blueBand: correction.blueBand,
                mode: correction.mode ? correction.mode : 'multi'
            };
            this.unifiedCompositesRequest = this.$parent.fetchUnifiedComposites(true)
                .then((composites) => {
                    this.unifiedComposites =
                        Object.assign(
                            {},
                            composites,
                            this.defaultColorModes
                        );
                    this.initProjectBuffer();
                    this.isLoading = false;
                    this.activeColorModeKey = this.initActiveColorMode();
                });
        });
    }

    initActiveColorMode() {
        const key = Object.keys(this.unifiedComposites).find(k => {
            const c = this.unifiedComposites[k].value;

            return (
                this.currentBands.redBand === c.redBand &&
                this.currentBands.greenBand === c.greenBand &&
                this.currentBands.blueBand === c.blueBand
            );
        });

        if (!key) {
            this.initCustomColorMode();
            return 'custom';
        }

        return key;
    }

    initCustomColorMode() {
        this.unifiedComposites.custom.value.redBand = this.currentBands.redBand;
        this.unifiedComposites.custom.value.greenBand = this.currentBands.greenBand;
        this.unifiedComposites.custom.value.blueBand = this.currentBands.blueBand;
        this.currentBands.mode = 'custom-rgb';
    }

    initProjectBuffer() {
        this.initSingleBandDefaults();
        this.projectBuffer = Object.assign({}, this.$parent.project);
        if (this.projectBuffer.isSingleBand) {
            this.initActiveScheme();
        }
    }

    initActiveScheme() {
        this.projectBuffer.singleBandOptions =
            Object.assign({}, this.defaultSingleBandOptions, this.projectBuffer.singleBandOptions);

        this.maskedValues = this.projectBuffer.singleBandOptions.extraNoData;

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
            legendOrientation: 'left',
            extraNoData: []
        };
    }

    hasNoBands(datasource) {
        return !datasource.bands.length;
    }

    getSerializedSingleBandOptions() {
        return angular.toJson(this.projectBuffer.singleBandOptions);
    }

    toggleProjectSingleBandMode(state) {
        this.initSingleBandDefaults();
        if (!this.projectBuffer) {
            return;
        } else if (typeof state !== 'undefined') {
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

    setActiveColorScheme(scheme, masked, save = false) {
        if (!this.isLoading) {
            this.activeColorScheme = scheme;
            this.activeColorSchemeType =
                this.colorSchemeService.defaultColorSchemeTypes.find(t => t.value === scheme.type);
            this.projectBuffer.singleBandOptions.dataType = scheme.type;
            this.projectBuffer.singleBandOptions.extraNoData = _.filter(masked, isFinite);
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

    onSchemeColorsChange({schemeColors, masked}) {
        const breaks = Object.keys(schemeColors);
        const colors = breaks.map(b => schemeColors[b]);

        this.setActiveColorScheme({
            label: 'Custom categorical scheme',
            mappedScheme: schemeColors,
            colors: colors,
            breaks: breaks,
            type: 'CATEGORICAL'
        }, masked, true);
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
            Object.assign(this.currentBands, this.getActiveColorMode().value);
            if (save) {
                this.saveCorrection();
            }
        }
    }

    isActiveColorMode(key) {
        if (!this.isLoading && this.projectBuffer) {
            return (
                !this.projectBuffer.isSingleBand &&
                key === this.activeColorModeKey
            );
        }
        return false;
    }

    setActiveBand(bandValue, bandName, save = true) {
        this.currentBands[bandName] = bandValue;

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
        this.savingCorrection = true;
        if (this.unifiedComposites) {
            this.projectService.setProjectColorMode(
                this.projectEditService.currentProject.id,
                _.pick(this.currentBands, ['redBand', 'greenBand', 'blueBand'])
            ).then(() => {
                this.redrawMosaic();
            }).finally(() => {
                this.savingCorrection = false;
            });
        } else if (this.unifiedCompositesRequest) {
            this.unifiedCompositesRequest.then(this.saveCorrection().bind(this), error => {
                this.$log.error(error);
                this.savingCorrection = false;
            });
        }
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

    correctionsDisabled() {
        return this.projectBuffer && this.projectBuffer.isSingleBand ||
                !this.projectBuffer ||
            this.$parent.pagination &&
            this.$parent.pagination.count > this.$parent.pagination.pageSize;
    }

    navToCorrections() {
        this.$state.go('projects.edit.advancedcolor');
    }
}

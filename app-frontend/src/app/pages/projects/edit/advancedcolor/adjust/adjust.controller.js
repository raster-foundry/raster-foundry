export default class ProjectsColorAdjustController {
    constructor( // eslint-disable-line max-params
        $log, $state, $scope, $timeout, histogramService
    ) {
        'ngInject';
        this.$log = $log;
        this.$state = $state;
        this.$parent = $scope.$parent.$ctrl;
        this.$scope = $scope;
        this.$timeout = $timeout;
        this.histogramService = histogramService;

        let baseGammaOptions = {
            floor: 0,
            ceil: 2,
            step: 0.01,
            showTicks: 0.25,
            precision: 2
        };

        let baseSaturationOptions = {
            floor: 0,
            ceil: 2,
            step: 0.01,
            showTicks: 0.25,
            precision: 2
        };

        let alphaOptions = {
            floor: 0,
            ceil: 2,
            step: 0.01,
            showTicks: 0.25,
            precision: 2
        };

        let betaOptions = {
            value: 0.6,
            floor: 0,
            ceil: 10,
            step: 0.1,
            precision: 2,
            showTicks: 1
        };

        let allGamma = ['redGamma', 'greenGamma', 'blueGamma'];

        this.redGammaOptions = Object.assign({
            id: 'redGamma',
            onEnd: (id, val) => {
                this.onFilterChange(
                    this.gammaLinkToggle ? allGamma : 'redGamma', val, this.redGammaOptions
                );
            }
        }, baseGammaOptions);
        this.greenGammaOptions = Object.assign({
            id: 'greenGamma',
            onEnd: (id, val) => {
                this.onFilterChange(
                    this.gammaLinkToggle ? allGamma : 'greenGamma', val, this.greenGammaOptions
                );
            }
        }, baseGammaOptions);
        this.blueGammaOptions = Object.assign({
            id: 'blueGamma',
            onEnd: (id, val) => {
                this.onFilterChange(
                    this.gammaLinkToggle ? allGamma : 'blueGamma', val, this.blueGammaOptions
                );
            }
        }, baseGammaOptions);

        this.saturationOptions = Object.assign({
            id: 'saturation',
            onEnd: (id, val) => {
                this.onFilterChange(id, val, baseSaturationOptions);
            }
        }, baseSaturationOptions);

        this.alphaOptions = Object.assign({
            id: 'alpha',
            onEnd: (id, val) => this.onFilterChange(id, val, this.alphaOptions)
        }, alphaOptions);

        this.betaOptions = Object.assign({
            id: 'beta',
            onEnd: (id, val) => this.onFilterChange(id, val, this.betaOptions)
        }, betaOptions);

        this.gammaLinkToggle = true;

        this.gammaToggle = { value: true };
        this.sigToggle = { value: true };
        this.minMaxToggle = { value: true };
        this.saturationToggle = { value: true };
    }

    $onInit() {
        if (!this.$parent.selectedScenes.size) {
            this.$state.go('^');
        }

        this.$scope.$watch('$ctrl.$parent.correction', this.$onChanges.bind(this));
        this.initHistogram();
    }

    initHistogram() {
        let sceneKeys = this.$parent.selectedScenes &&
            Array.from(this.$parent.selectedScenes.keys());
        if (!sceneKeys || !sceneKeys.length) {
            return;
        }
        this.histogramData = [];
        this.histogramService.getHistogram(
            this.$state.params.projectid,
            Array.from(sceneKeys)
        ).then((response) => {
            this.histogramData = response.data;
        });
    }

    /**
      * When corrections are initialized outside the controller, infer disabled status
      * @param {object} correction updated correction
      * @returns {undefined}
      */
    $onChanges(correction) {
        if (correction) {
            this.correction = correction;

            this.gammaLinkToggle = this.correction.redGamma === this.correction.blueGamma &&
                this.correction.blueGamma === this.correction.greenGamma;

            if (this.correction.redGamma === null &&
                this.correction.greenGamma === null &&
                this.correction.blueGamma === null) {
                this.gammaToggle.value = false;
            } else {
                this.gammaToggle.value = true;
            }

            if (this.correction.saturation === null) {
                this.saturationToggle.value = false;
            }

            if (this.correction.alpha === null &&
                this.correction.beta === null) {
                this.sigToggle.value = false;
            } else {
                this.sigToggle.value = true;
            }

            let defaultMinMax = {};

            if (this.correction.min === null &&
                this.correction.max === null) {
                this.minMaxToggle.value = false;

                this.setDefaultsForEnabled();

                defaultMinMax.min = 0;
                defaultMinMax.max = 65535;
            } else {
                this.minMax = true;

                this.setDefaultsForEnabled();

                defaultMinMax.min = this.correction.min;
                defaultMinMax.max = this.correction.max;
            }

            this.sliderCorrection = Object.assign(defaultMinMax, this.correction);
            this.$timeout(() => {
                this.$scope.$broadcast('rzSliderForceRender');
            });
        }
    }

    /**
     * For any enabled correction categories, set valid values
     * @returns {undefined}
     */
    setDefaultsForEnabled() {
        let defaults = {
            redGamma: 1,
            greenGamma: 1,
            blueGamma: 1,
            saturation: 1,
            alpha: 0.2,
            beta: 13
        };
        let correction = this.correction;
        if (this.gammaToggle.value) {
            if (correction.redGamma === null) {
                correction.redGamma = defaults.redGamma;
            }
            if (correction.greenGamma === null) {
                correction.greenGamma = defaults.greenGamma;
            }
            if (correction.blueGamma === null) {
                correction.blueGamma = defaults.blueGamma;
            }
        }

        if (this.saturationToggle.value) {
            if (correction.saturation === null) {
                correction.saturation = defaults.saturation;
            }
        }

        if (this.sigToggle.value) {
            if (correction.alpha === null) {
                correction.alpha = defaults.alpha;
            }
            if (correction.beta === null) {
                correction.beta = defaults.beta;
            }
        }
    }

    onGammaFilterChange(id, val) {
        let relevantIds = id;
        if (this.gammaLinkToggle) {
            relevantIds = ['redGamma', 'greenGamma', 'blueGamma'];
        }
        this.onFilterChange(relevantIds, val);
    }

    /**
     * Makes color correction changes available as a component rgbSum
     *
     * @param {string} id used to identify correction that has been modified
     * @param {number} val new value for a color correction
     * @param {object} options todo
     * @returns {null} null
     */
    onFilterChange(id, val) {
        if (Array.isArray(id)) {
            id.forEach((key) => {
                this.correction[key] = val;
            });
        } else if (id) {
            this.correction[id] = val;
        }

        this.sliderCorrection = Object.assign({}, this.correction);
        this.$parent.onCorrectionChange(Object.assign({}, this.correction));

        this.$timeout(() => {
            this.$scope.$broadcast('rzSliderForceRender');
            this.initHistogram();
        });
    }

    gammaLinkToggled() {
        this.gammaLinkToggle = !this.gammaLinkToggle;
        if (this.gammaLinkToggle) {
            this.onFilterChange(
                ['redGamma', 'greenGamma', 'blueGamma'],
                this.correction.redGamma, this.redGammaOptions);
        }
    }

    gammaToggled() {
        const value = !this.gammaToggle.value;
        this.gammaToggle.value = value;
        if (!value) {
            this.correction.redGamma = null;
            this.correction.greenGamma = null;
            this.correction.blueGamma = null;
        }
        this.setDefaultsForEnabled();
        this.onFilterChange();
    }

    saturationToggled() {
        const value = !this.saturationToggle.value;
        this.saturationToggle.value = value;
        if (!value) {
            this.correction.saturation = null;
        }
        this.setDefaultsForEnabled();
        this.onFilterChange();
    }

    sigToggled() {
        const value = !this.sigToggle.value;
        this.sigToggle.value = value;
        if (!value) {
            this.correction.alpha = null;
            this.correction.beta = null;
        }
        this.setDefaultsForEnabled();
        this.onFilterChange();
    }

    onHistogramChange(clipping) {
        let clipParams = {
            min: clipping.rgb.min,
            max: clipping.rgb.max,

            redMin: clipping.red.min !== clipping.rgb.min ? clipping.red.min : null,
            redMax: clipping.red.max !== clipping.rgb.max ? clipping.red.max : null,

            greenMin: clipping.green.min !== clipping.rgb.min ? clipping.green.min : null,
            greenMax: clipping.green.max !== clipping.rgb.max ? clipping.green.max : null,

            blueMin: clipping.blue.min !== clipping.rgb.min ? clipping.blue.min : null,
            blueMax: clipping.blue.max !== clipping.rgb.max ? clipping.blue.max : null
        };
        Object.assign(this.correction, clipParams);
        this.$parent.onCorrectionChange(
            Object.assign({}, this.correction)
        ).then(() => {
            this.initHistogram();
        });
    }
}

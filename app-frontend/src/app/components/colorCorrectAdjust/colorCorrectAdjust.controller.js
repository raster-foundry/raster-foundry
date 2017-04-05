export default class ColorCorrectAdjustController {
    $onInit() {
        let baseGammaOptions = {
            floor: 0,
            ceil: 2,
            step: 0.1,
            precision: 1,
            showTicks: 0.25
        };

        let baseFilterOptions = {
            floor: -60,
            ceil: 60,
            step: 1,
            showTicks: 10
        };

        let alphaOptions = {
            value: 0.6,
            floor: 0,
            ceil: 1,
            step: 0.01,
            precision: 2,
            showTicks: 0.2
        };

        let betaOptions = {
            value: 10,
            floor: 0,
            ceil: 50,
            step: 1,
            showTicks: 10
        };

        let minMaxOptions = {
            floor: 0,
            ceil: 65535,
            step: 10
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

        this.alphaOptions = Object.assign({
            id: 'alpha',
            onEnd: (id, val) => this.onFilterChange(id, val, this.alphaOptions)
        }, alphaOptions);
        this.betaOptions = Object.assign({
            id: 'beta',
            onEnd: (id, val) => this.onFilterChange(id, val, this.betaOptions)
        }, betaOptions);

        this.brightnessOptions = Object.assign({
            id: 'brightness',
            onEnd: (id, val) => this.onFilterChange(id, val, this.brightnessOptions)
        }, baseFilterOptions);
        this.contrastOptions = Object.assign({
            id: 'contrast',
            onEnd: (id, val) => this.onFilterChange(id, val, this.contrastOptions)
        }, baseFilterOptions);

        this.minMaxOptions = Object.assign({
            id: 'minmax',
            onEnd: (id, low, high) => {
                this.onFilterChange('min', low, this.minMaxOptions);
                this.onFilterChange('max', high, this.minMaxOptions);
            }
        }, minMaxOptions);

        this.gammaLinkToggle = true;

        this.gammaToggle = {value: true};
        this.sigToggle = {value: true};
        this.bcToggle = {value: true};
        this.minMaxToggle = {value: true};
    }

    /**
      * When corrections are initialized outside the controller, infer disabled status
      * @param {object} changes changes object
      * @returns {undefined}
      */
    $onChanges(changes) {
        if ('correction' in changes && changes.correction.currentValue) {
            this.correction = changes.correction.currentValue;

            this.gammaLinkToggle = this.correction.redGamma === this.correction.blueGamma &&
                this.correction.blueGamma === this.correction.greenGamma;

            if (this.correction.redGamma === null &&
                this.correction.greenGamma === null &&
                this.correction.blueGamma === null) {
                this.redGammaOptions.disabled = true;
                this.greenGammaOptions.disabled = true;
                this.blueGammaOptions.disabled = true;
                this.gammaToggle.value = false;
            } else {
                this.redGammaOptions.disabled = false;
                this.greenGammaOptions.disabled = false;
                this.blueGammaOptions.disabled = false;
                this.gammaToggle.value = true;
            }

            if (this.correction.alpha === null &&
                this.correction.beta === null) {
                this.alphaOptions.disabled = true;
                this.betaOptions.disabled = true;
                this.sigToggle.value = false;
            } else {
                this.alphaOptions.disabled = false;
                this.betaOptions.disabled = false;
                this.sigToggle.value = true;
            }

            if (this.correction.brightness === null &&
                (this.correction.contrast === null || this.correction.contract === 0)) {
                this.brightnessOptions.disabled = true;
                this.contrastOptions.disabled = true;
                this.correction.contrast = 0;
                this.bcToggle.value = false;
            } else {
                this.brightnessOptions.disabled = false;
                this.contrastOptions.disabled = false;
                this.bcToggle.value = true;
            }

            if (this.correction.min === null &&
                this.correction.max === null) {
                this.minMaxOptions.disabled = true;
                this.minMaxToggle = false;

                this.setDefaultsForEnabled();

                this.sliderCorrection.min = 0;
                this.sliderCorrection.max = 65535;
            } else {
                this.minMaxOptions.disabled = false;
                this.minMax = true;

                this.setDefaultsForEnabled();

                this.sliderCorrection.min = this.correction.min;
                this.sliderCorrection.max = this.correction.max;
            }
            this.sliderCorrection = Object.assign({}, this.correction);
        }
    }

    /**
     * For any enabled correction categories, set valid values
     * @returns {undefined}
     */
    setDefaultsForEnabled() {
        let defaults = {
            redGamma: 0.5,
            greenGamma: 0.5,
            blueGamma: 0.5,
            brightness: -6,
            contrast: 0,
            alpha: 0.2,
            beta: 13,
            min: 0,
            max: 65535
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

        if (this.sigToggle.value) {
            if (correction.alpha === null) {
                correction.alpha = defaults.alpha;
            }
            if (correction.beta === null) {
                correction.beta = defaults.beta;
            }
        }

        if (this.bcToggle.value) {
            if (correction.brightness === null && correction.contrast === 0) {
                correction.contrast = defaults.contrast;
            }
            if (correction.brightness === null) {
                correction.brightness = defaults.brightness;
            }
        }

        if (this.minMaxToggle.value) {
            if (correction.min === null) {
                correction.min = defaults.min;
            }
            if (correction.max === null) {
                correction.max = defaults.max;
            }
        }
    }

    /**
     * Makes color correction changes available as a component output
     *
     * @param {string} id used to identify correction that has been modified
     * @param {number} val new value for a color correction
     * @param {object} options todo
     * @returns {null} null
     */
    onFilterChange(id, val, options) {
        if (Array.isArray(id)) {
            id.forEach((key) => {
                if (!options.disabled) {
                    this.correction[key] = val;
                } else {
                    this.correction[key] = null;
                }
            });
        } else if (id && !options.disabled) {
            this.correction[id] = val;
        } else if (id) {
            this.correction[id] = null;
        }
        this.sliderCorrection = Object.assign({}, this.correction);
        this.onCorrectionChange({newCorrection: Object.assign({}, this.correction)});
    }

    gammaLinkToggled() {
        this.gammaLinkToggle = !this.gammaLinkToggle;
        if (this.gammaLinkToggle) {
            this.onFilterChange(
                ['redGamma', 'greenGamma', 'blueGamma'],
                this.correction.redGamma, this.redGammaOptions);
        }
    }

    gammaToggled(value) {
        this.redGammaOptions.disabled = !value;
        this.greenGammaOptions.disabled = !value;
        this.blueGammaOptions.disabled = !value;
        if (!value) {
            this.correction.redGamma = null;
            this.correction.greenGamma = null;
            this.correction.blueGamma = null;
        }
        this.setDefaultsForEnabled();
        this.onFilterChange();
    }

    sigToggled(value) {
        this.alphaOptions.disabled = !value;
        this.betaOptions.disabled = !value;
        if (!value) {
            this.correction.alpha = null;
            this.correction.beta = null;
        }
        this.setDefaultsForEnabled();
        this.onFilterChange();
    }

    bcToggled(value) {
        this.brightnessOptions.disabled = !value;
        this.contrastOptions.disabled = !value;
        this.minMaxOptions.disabled = !value;
        if (!value) {
            this.correction.brightness = null;
            this.correction.contrast = 0;
        }
        this.setDefaultsForEnabled();
        this.onFilterChange();
    }

    minMaxToggled(value) {
        this.minMaxOptions.disabled = !value;
        if (!value) {
            this.correction.min = null;
            this.correction.max = null;
        }
        this.setDefaultsForEnabled();
        this.onFilterChange();
    }
}

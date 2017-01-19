export default class ColorCorrectAdjustController {
    constructor() {
        'ngInject';
    }

    $onInit() {
        let baseGammaOptions = {
            value: 1,
            floor: 0,
            ceil: 2,
            step: 0.1,
            precision: 1,
            showTicks: 0.25,
            onEnd: (id, val) => this.onFilterChange(id, val)
        };

        let baseFilterOptions = {
            floor: -60,
            ceil: 60,
            step: 1,
            showTicks: 10,
            onEnd: (id, val) => this.onFilterChange(id, val)
        };

        let alphaOptions = {
            value: 0.6,
            floor: 0,
            ceil: 1,
            step: 0.1,
            precision: 2,
            showTicks: 0.2,
            onEnd: (id, val) => this.onFilterChange(id, val)
        };

        let betaOptions = {
            value: 10,
            floor: 0,
            ceil: 50,
            step: 1,
            showTicks: 10,
            onEnd: (id, val) => this.onFilterChange(id, val)
        };

        let minMaxOptions = {
            floor: 0,
            ceil: 20000,
            step: 10,
            onEnd: (id, low, high) => {
                this.onFilterChange('min', low);
                this.onFilterChange('max', high);
            }
        };

        this.redGammaOptions = Object.assign({}, baseGammaOptions, {id: 'red'});
        this.greenGammaOptions = Object.assign({}, baseGammaOptions, {id: 'green'});
        this.blueGammaOptions = Object.assign({}, baseGammaOptions, {id: 'blue'});

        this.alphaOptions = Object.assign({}, alphaOptions, {id: 'alpha'});
        this.betaOptions = Object.assign({}, betaOptions, {id: 'beta'});

        this.brightnessOptions = Object.assign({}, baseFilterOptions, {id: 'brightness'});
        this.contrastOptions = Object.assign({}, baseFilterOptions, {id: 'contrast'});
        this.minMaxOptions = Object.assign({}, minMaxOptions, {id: 'minmax'});

        this.gammaToggle = {value: true};
        this.sigToggle = {value: true};
        this.bcToggle = {value: true};
    }

    /**
     * Makes color correction changes available as a component output
     *
     * @param {string} id used to identify correction that has been modified
     * @param {number} val new value for a color correction
     * @returns {null} null
     */
    onFilterChange(id, val) {
        if (id) {
            this.correction[id] = val;
        }
        this.onCorrectionChange({newCorrection: Object.assign({}, this.correction)});
    }

    gammaToggled(value) {
        this.redGammaOptions.disabled = !value;
        this.greenGammaOptions.disabled = !value;
        this.blueGammaOptions.disabled = !value;
        ['red', 'green', 'blue'].forEach((id) => {
            this.correction[id] = null;
        });
        this.onFilterChange();
    }

    sigToggled(value) {
        this.alphaOptions.disabled = !value;
        this.betaOptions.disabled = !value;
        ['alpha', 'beta'].forEach((id) => {
            this.correction[id] = null;
        });
        this.onFilterChange();
    }

    bcToggled(value) {
        this.brightnessOptions.disabled = !value;
        this.contrastOptions.disabled = !value;
        this.minMaxOptions.disabled = !value;
        ['brightness', 'contrast', 'min', 'max'].forEach((id) => {
            this.correction[id] = null;
        });
        this.onFilterChange();
    }
}

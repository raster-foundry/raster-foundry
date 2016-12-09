export default class ColorCorrectAdjustController {
    constructor() {
        'ngInject';
    }

    $onInit() {
        let baseGammaOptions = {
            floor: 0,
            ceil: 2,
            step: 0.1,
            precision: 1,
            onEnd: (id, val) => this.onFilterChange(id, val)
        };

        let baseFilterOptions = {
            floor: 0,
            ceil: 60,
            step: 1,
            onEnd: (id, val) => this.onFilterChange(id, val)
        };

        let alphaOptions = {
            floor: 0,
            disabled: true,
            ceil: 1,
            step: 0.1,
            precision: 2,
            onEnd: (id, val) => this.onFilterChange(id, val)
        };

        let betaOptions = {
            floor: 0,
            disabled: true,
            ceil: 50,
            step: 1,
            onEnd: (id, val) => this.onFilterChange(id, val)
        };

        let minMaxOptions = {
            floor: 0,
            ceil: 20000,
            step: 10,
            onEnd: (id, val) => this.onFilterChange(id, val)
        };

        this.redGammaOptions = Object.assign({}, baseGammaOptions, {id: 'red'});
        this.greenGammaOptions = Object.assign({}, baseGammaOptions, {id: 'green'});
        this.blueGammaOptions = Object.assign({}, baseGammaOptions, {id: 'blue'});

        this.brightnessOptions = Object.assign({}, baseFilterOptions, {id: 'brightness'});
        this.contrastOptions = Object.assign({}, baseFilterOptions, {id: 'contrast'});

        this.alphaOptions = Object.assign({}, alphaOptions, {id: 'alpha'});
        this.betaOptions = Object.assign({}, betaOptions, {id: 'beta'});
        this.minMaxOptions = Object.assign({}, minMaxOptions, {id: 'minmax'});
    }

    $onChanges(changesObj) {
        if ('correction' in changesObj) {
            this.myCorrection = Object.assign({}, changesObj.correction.currentValue);
        }
    }

    /**
     * Makes color correction changes available as a component output
     *
     * @param {string} id used to identify correction that has been modified
     * @param {number} val new value for a color correction
     * @returns {null} null
     */
    onFilterChange(id, val) {
        this.myCorrection[id] = val;
        this.onCorrectionChange({newCorrection: Object.assign({}, this.myCorrection)});
    }
}

export default class ColorCorrectPaneController {
    constructor( // eslint-disable-line max-params
        $log, $scope, $q, projectService, $state
    ) {
        'ngInject';
        this.bucketService = projectService;
        this.$state = $state;
        this.$q = $q;
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

        this.redGammaOptions = Object.assign({}, baseGammaOptions, {id: 'redGamma'});
        this.greenGammaOptions = Object.assign({}, baseGammaOptions, {id: 'greenGamma'});
        this.blueGammaOptions = Object.assign({}, baseGammaOptions, {id: 'blueGamma'});

        this.brightnessOptions = Object.assign({}, baseFilterOptions, {id: 'brightness'});
        this.contrastOptions = Object.assign({}, baseFilterOptions, {id: 'contrast'});

        this.alphaOptions = Object.assign({}, alphaOptions, {id: 'alpha'});
        this.betaOptions = Object.assign({}, betaOptions, {id: 'beta'});
        this.minMaxOptions = Object.assign({}, minMaxOptions, {id: 'minmax'});

        // Initialize correction to first selected layer (if there are multiple)
        this.correction = this.selectedLayers.values().next().value.baseColorCorrection();
    }

    /**
     * Triggered when filters are changed
     *
     * Applies color corrections to all selected layers
     * @param {string} id used to identify correction that has been modified
     * @param {number} val new value for a color correction
     *
     * @returns {null} null
     */
    onFilterChange(id, val) {
        this.correction[id] = val;
        let correction = this.correction;
        this.selectedLayers.forEach(function (layer) {
            layer.colorCorrect(correction);
        });
        this.onCorrectionChange({newCorrection: Object.assign({}, this.correction)});
    }
}

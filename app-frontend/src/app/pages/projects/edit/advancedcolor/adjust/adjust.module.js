import angular from 'angular';

class ProjectsColorAdjustController {
    constructor( // eslint-disable-line max-params
        $log, $state, $scope, $timeout, projectHistogramService
    ) {
        'ngInject';
        this.$log = $log;
        this.$state = $state;
        this.$parent = $scope.$parent.$ctrl;
        this.$scope = $scope;
        this.$timeout = $timeout;
        this.histogramService = projectHistogramService;

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
            value: 1,
            floor: 0,
            ceil: 1,
            step: 0.01,
            showTicks: 0.25,
            precision: 2
        };

        let betaOptions = {
            value: 1,
            floor: 0,
            ceil: 10,
            step: 0.1,
            precision: 2,
            showTicks: 1
        };

        this.redGammaOptions = Object.assign({
            id: 'redGamma',
            onEnd: (id, val) => this.onGammaFilterChange(val)
        }, baseGammaOptions);
        this.greenGammaOptions = Object.assign({
            id: 'greenGamma',
            onEnd: (id, val) => this.onGammaFilterChange(val)
        }, baseGammaOptions);
        this.blueGammaOptions = Object.assign({
            id: 'blueGamma',
            onEnd: (id, val) => this.onGammaFilterChange(val)
        }, baseGammaOptions);

        this.saturationOptions = Object.assign({
            id: 'saturation',
            onEnd: () => this.onFilterChange()
        }, baseSaturationOptions);

        this.alphaOptions = Object.assign({
            id: 'alpha',
            onEnd: () => this.onFilterChange()
        }, alphaOptions);

        this.betaOptions = Object.assign({
            id: 'beta',
            onEnd: () => this.onFilterChange()
        }, betaOptions);

        this.gammaLinkToggle = true;
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
        this.loadingHistogram = true;
        this.histogramService.getHistogram(
            this.$state.params.projectid,
            Array.from(sceneKeys)
        ).then((response) => {
            this.histogramData = response.data;
            this.loadingHistogram = false;
        });
    }

    /**
      *
      * @param {object} correction updated correction
      * @returns {undefined}
      */
    $onChanges(correction) {
        if (correction) {
            this.correction = Object.assign({}, this.correction, correction);

            this.gammaLinkToggle = this.correction.gamma.enabled === this.correction.redGamma &&
                this.correction.blueGamma === this.correction.greenGamma;

            this.$timeout(() => {
                this.$scope.$broadcast('rzSliderForceRender');
            });
        }
    }

    /**
      * Account for slider linkage when doing gamma correction
      *
      * @param {int} val The new value which should be propagated to all bands if linked
      * @returns {undefined}
      */
    onGammaFilterChange(val) {
        if (this.gammaLinkToggle) {
            this.correction.gamma.redGamma = val;
            this.correction.gamma.greenGamma = val;
            this.correction.gamma.blueGamma = val;
        }
        this.onFilterChange();
    }

    /**
     * Makes color correction changes available as a component rgbSum
     *
     * @returns {undefined}
     */
    onFilterChange() {
        this.$parent.onCorrectionChange(Object.assign({}, this.correction))
            .then(() => {
                this.$scope.$broadcast('rzSliderForceRender');
                this.initHistogram();
                this.$scope.$evalAsync();
            });
    }

    gammaLinkToggled() {
        this.gammaLinkToggle = !this.gammaLinkToggle;
        if (this.gammaLinkToggle) {
            this.correction.gamma.greenGamma = this.correction.gamma.redGamma;
            this.correction.gamma.blueGamma = this.correction.gamma.redGamma;
        }
        this.onFilterChange();
    }

    storeToggle(value, correctionName, paramsArray) {
        this.correction[correctionName].enabled = value;
        if (value) {
            this.setCorrectionDefault(correctionName, paramsArray);
        }
        this.onFilterChange();
    }

    onHistogramChange(clipping) {
        let tileClip = this.correction.tileClipping;
        tileClip.min = clipping.rgb.min;
        tileClip.max = clipping.rgb.max;
        tileClip.enabled = typeof clipping.rgb.min !== 'undefined' ||
            typeof clipping.rgb.max !== 'undefined';

        let bandClip = this.correction.bandClipping;
        bandClip.redMin = clipping.red.min;
        bandClip.redMax = clipping.red.max;
        bandClip.greenMin = clipping.green.min;
        bandClip.greenMax = clipping.green.max;
        bandClip.blueMin = clipping.blue.min;
        bandClip.blueMax = clipping.blue.max;
        bandClip.enabled =
            typeof clipping.red.min !== 'undefined' ||
            typeof clipping.red.max !== 'undefined' ||
            typeof clipping.green.min !== 'undefined' ||
            typeof clipping.green.max !== 'undefined' ||
            typeof clipping.blue.min !== 'undefined' ||
            typeof clipping.blue.max !== 'undefined';
        this.onFilterChange();
    }

    setCorrectionDefault(correctionName, paramsArray) {
        paramsArray.forEach((param) => {
            this.correction[correctionName][param] = 1;
        });
    }
}

const ProjectsColorAdjustModule = angular.module('pages.projects.edit.advancedcolor.adjust', []);

ProjectsColorAdjustModule.controller(
    'ProjectsColorAdjustController', ProjectsColorAdjustController
);

export default ProjectsColorAdjustModule;

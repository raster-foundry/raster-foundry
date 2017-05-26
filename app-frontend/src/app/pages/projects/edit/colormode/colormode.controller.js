export default class ProjectsEditColormode {
    constructor($scope, colorCorrectService, projectService) {
        'ngInject';
        this.$scope = $scope;
        this.$parent = $scope.$parent.$ctrl;
        this.colorCorrectService = colorCorrectService;
        this.projectService = projectService;
    }

    $onInit() {
        this.currentBands = null;
        this.correction = null;
        this.$parent.sceneListQuery.then(() => {
            let layer = this.$parent.sceneLayers.values().next();
            if (layer && layer.value) {
                layer.value.getColorCorrection().then((correction) => {
                    this.currentBands = {
                        redBand: correction.redBand,
                        greenBand: correction.greenBand,
                        blueBand: correction.blueBand
                    };
                    this.correction = correction;
                });
            }
        });
    }

    isActiveColorMode(key) {
        const unifiedComposites = this.$parent.unifiedComposites;
        if (unifiedComposites) {
            let keyBands = unifiedComposites[key].value;

            let isActive = this.currentBands &&
                keyBands.redBand === this.currentBands.redBand &&
                keyBands.greenBand === this.currentBands.greenBand &&
                keyBands.blueBand === this.currentBands.blueBand;

            return isActive;
        }
        return false;
    }

    setBands(bandName) {
        const unifiedComposites = this.$parent.unifiedComposites;
        if (unifiedComposites) {
            this.currentBands = unifiedComposites[bandName].value;
            this.correction = Object.assign(this.correction, this.currentBands);
            const promise = this.colorCorrectService.bulkUpdate(
                this.projectService.currentProject.id,
                Array.from(this.$parent.sceneLayers.keys()),
                this.correction
            );
            this.redrawMosaic(promise);
        }
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

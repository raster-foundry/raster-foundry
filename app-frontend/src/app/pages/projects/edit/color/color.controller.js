export default class ProjectsEditColorController {
    constructor( // eslint-disable-line max-params
        $scope, projectService, projectEditService, colorCorrectService
    ) {
        'ngInject';
        this.$scope = $scope;
        this.$parent = $scope.$parent.$ctrl;
        this.projectService = projectService;
        this.colorCorrectService = colorCorrectService;
    }

    $onInit() {
        this.currentBands = null;
        this.correction = {};
        this.projectEditService.fetchCurrentProject().then(() => {
            this.$parent.getSceneList().then(() => {
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
        });
    }

    isActiveAutoColorCorrection(correctionType) {
        if (this.correction && this.correction[correctionType]) {
            return this.correction[correctionType].enabled;
        }
        return false;
    }

    setAutoColorCorrection(correctionType) {
        if (correctionType in this.correction) {
            this.correction[correctionType].enabled = !this.correction[correctionType].enabled;
        } else {
            this.correction[correctionType] = {};
            this.correction[correctionType].enabled = true;
        }
        const promise = this.colorCorrectService.bulkUpdate(
            this.projectService.currentProject.id,
            Array.from(this.$parent.sceneLayers.keys()),
            this.correction
        );
        this.redrawMosaic(promise);
    }

    redrawMosaic(promise) {
        promise.then(() => {
            this.$parent.layerFromProject();
        });
    }
}

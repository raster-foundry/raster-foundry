export default class DatasourceCreateModalController {
    constructor(datasourceService) {
        'ngInject';
        this.datasourceService = datasourceService;
    }

    $onInit() {
        this.steps = [
            'NAME',
            'SUCCESS'
        ];
        this.currentStep = this.steps[0];
        this.datasourceBuffer = {
            name: null,
            composites: {
                natural: {
                    label: 'Default',
                    value: {
                        redBand: 0,
                        greenBand: 1,
                        blueBand: 2
                    }
                }
            }
        };

        this.isCreatingDatasource = false;
    }

    createDatasource() {
        return this.datasourceService.createDatasource(
            this.datasourceBuffer.name,
            this.datasourceBuffer.composites
        );
    }

    currentStepIs(step) {
        return this.currentStep === step;
    }

    handleCreate() {
        this.createDatasource()
            .then(ds => {
                this.currentStep = 'SUCCESS';
                this.datasource = ds;
            },
            () => {
                this.showError = true;
            }).finally(() => {
                this.isCreatingDatasource = false;
            });
        this.isCreatingDatasource = true;
    }

}

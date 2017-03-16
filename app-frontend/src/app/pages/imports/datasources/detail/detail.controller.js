class DatasourceDetailController {
    constructor( // eslint-disable-line max-params
        $stateParams, datasourceService
    ) {
        'ngInject';
        this.datasourceId = $stateParams.datasourceid;
        this.datasourceService = datasourceService;
    }

    $onInit() {
        this.loadDatasource();
    }

    loadDatasource() {
        this.isLoadingDatasource = true;
        this.isLoadingDatasourceError = false;
        this.datasourceService.get(this.datasourceId).then(
            datasourceResponse => {
                this.datasource = datasourceResponse;
            },
            () => {
                this.isLoadingDatasourceError = true;
            }
        ).finally(() => {
            this.isLoadingDatasource = false;
        });
    }

    parseColorCorrection() {
        this.colorCorrectionBuffer = Object.assign({}, this.datasource.colorCorrection);
        console.log(this.colorCorrectionBuffer);
    }

    parseColorComposites() {
        this.colorCompositesBuffer = Object.assign({}, this.datasource.composites);
        console.log(this.colorCompositesBuffer);
    }
}

export default DatasourceDetailController;

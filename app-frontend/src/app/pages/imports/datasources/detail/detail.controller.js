class DatasourceDetailController {
    constructor(
        $stateParams, $uibModal, datasourceService
    ) {
        'ngInject';
        this.$uibModal = $uibModal;
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
                this.initBuffers();
            },
            () => {
                this.isLoadingDatasourceError = true;
            }
        ).finally(() => {
            this.isLoadingDatasource = false;
        });
    }

    initBuffers() {
        this.colorCorrectionBuffer = Object.assign({}, this.datasource.colorCorrection);
        this.colorCompositesBuffer = Object.assign({}, this.datasource.composites);
    }

    openImportModal() {
        if (this.activeModal) {
            this.activeModal.dismiss();
        }

        this.activeModal = this.$uibModal.open({
            component: 'rfImportModal',
            resolve: {
                datasource: () => this.datasource
            }
        });

        this.activeModal.result.then(() => {

        });

        return this.activeModal;
    }

    saveColorCorrection() {
        // @TODO: implement, replace json with buffer
    }

    saveColorComposites() {
        // @TODO: implement, replace json with buffer
    }

    cancel() {
        this.initBuffers();
    }
}

export default DatasourceDetailController;

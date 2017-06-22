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
        this.isDatasourceVisibilityUpdated = false;
        this.datasourceService.get(this.datasourceId).then(
            datasourceResponse => {
                this.datasource = datasourceResponse;
                this.isPublic = this.isPublicDatasource();
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
        this.colorCompositesBuffer = Object.assign({}, this.datasource.composites);
    }

    openImportModal() {
        if (this.activeModal) {
            this.activeModal.dismiss();
        }

        this.activeModal = this.$uibModal.open({
            component: 'rfSceneImportModal',
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

    notDefaultDatasource() {
        if (this.datasource) {
            return this.datasource.owner !== 'default';
        }
        return false;
    }

    isPublicDatasource() {
        if (this.datasource) {
            return this.datasource.visibility === 'PUBLIC';
        }
        return false;
    }

    changeVisibility() {
        this.datasource.visibility = this.datasource.visibility === 'PUBLIC' ? 'PRIVATE' : 'PUBLIC';
        this.isPublic = !this.isPublic;
        this.datasourceService.updateDatasource(this.datasource).then(
            () => {
                this.isDatasourceVisibilityUpdated = true;
            },
            () => {
                this.isDatasourceVisibilityUpdated = false;
            }
        );
    }
}

export default DatasourceDetailController;

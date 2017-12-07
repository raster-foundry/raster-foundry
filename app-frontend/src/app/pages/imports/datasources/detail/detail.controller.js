/* global BUILDCONFIG */

class DatasourceDetailController {
    constructor(
        $stateParams, modalService, datasourceService
    ) {
        'ngInject';
        this.modalService = modalService;
        this.datasourceId = $stateParams.datasourceid;
        this.datasourceService = datasourceService;
    }

    $onInit() {
        this.BUILDCONFIG = BUILDCONFIG;
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
        this.modalService.open({
            component: 'rfSceneImportModal',
            resolve: {
                datasource: () => this.datasource
            }
        });
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

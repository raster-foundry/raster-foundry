class ImportsListController {
    constructor(authService, $uibModal) {
        'ngInject';
        this.authService = authService;
        this.$uibModal = $uibModal;
    }

    $onInit() {

    }

    $onDestroy() {

    }

    importModal() {
        if (this.activeModal) {
            this.activeModal.dismiss();
        }

        this.activeModal = this.$uibModal.open({
            component: 'rfSceneImportModal',
            resolve: {}
        });
    }

    openCreateDatasourceModal() {
        if (this.activeModal) {
            this.activeModal.dismiss();
        }

        this.activeModal = this.$uibModal.open({
            component: 'rfDatasourceCreateModal'
        });

        this.activeModal.result.then(() => {

        });

        return this.activeModal;
    }
}

export default ImportsListController;

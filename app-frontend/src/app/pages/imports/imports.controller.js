/* global BUILDCONFIG */

class ImportsController {
    constructor(authService, $uibModal) {
        'ngInject';
        this.authService = authService;
        this.$uibModal = $uibModal;
    }

    $onInit() {
        this.BUILDCONFIG = BUILDCONFIG;
    }

    $onDestroy() {

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

export default ImportsController;

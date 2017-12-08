/* global BUILDCONFIG */

class ImportsController {
    constructor(authService, modalService) {
        'ngInject';
        this.authService = authService;
        this.modalService = modalService;
    }

    $onInit() {
        this.BUILDCONFIG = BUILDCONFIG;
    }

    openCreateDatasourceModal() {
        this.modalService.open({
            component: 'rfDatasourceCreateModal'
        });
    }
}

export default ImportsController;

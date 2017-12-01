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
        return this.modalService.open({
            component: 'rfDatasourceCreateModal'
        });
    }

}

export default ImportsController;

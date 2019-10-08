/* global BUILDCONFIG HELPCONFIG */

class HomeController {
    constructor(authService, modalService) {
        'ngInject';
        this.authService = authService;
        this.modalService = modalService;
    }

    $onInit() {
        this.BUILDCONFIG = BUILDCONFIG;
        this.HELPCONFIG = HELPCONFIG;
    }

    openCreateProjectModal() {
        this.modalService.open({
            component: 'rfProjectCreateModal'
        }).result.catch(() => {});
    }

    openTemplateCreateModal() {
        this.modalService.open({
            component: 'rfTemplateCreateModal'
        }).result.catch(() => {});
    }
}

export default HomeController;

class HomeController {
    constructor(authService, $uibModal) {
        'ngInject';
        this.authService = authService;
        this.$uibModal = $uibModal;
    }

    $onInit() {

    }

    $onDestroy() {

    }

    openCreateProjectModal() {
        if (this.activeModal) {
            this.activeModal.dismiss();
        }

        this.activeModal = this.$uibModal.open({
            component: 'rfCreateProjectModal'
        });

        this.activeModal.result.then(() => {

        });

        return this.activeModal;
    }
}

export default HomeController;

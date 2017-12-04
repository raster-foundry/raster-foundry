export default (app) => {
    class ModalService {
        constructor($uibModal) {
            this.$uibModal = $uibModal;
            this.activeModal = false;
        }

        open(modalConfig, closeActive = true) {
            if (closeActive) {
                this.closeActiveModal();
            }

            const modal = this.$uibModal.open(modalConfig);
            this.activeModal = modal;
            return modal;
        }

        closeActiveModal() {
            if (this.activeModal) {
                this.activeModal.dismiss();
            }
        }
    }

    app.service('modalService', ModalService);
};

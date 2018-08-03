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

            this.activeModal = this.$uibModal.open(modalConfig);
            this.activeModal.result.finally(() => {
                delete this.activeModal;
            });
            return this.activeModal;
        }

        closeActiveModal() {
            if (this.activeModal) {
                this.activeModal.dismiss();
            }
        }
    }

    app.service('modalService', ModalService);
};

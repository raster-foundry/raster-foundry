export default (app) => {
    class ModalService {
        constructor($uibModal) {
            this.$uibModal = $uibModal;
            this.activeModal = false;
            this.persistentModal = false;
        }

        open(modalConfig, closeActive = true, persist = false) {
            this.persistentModal = persist;

            if (closeActive) {
                this.closeActiveModal(true);
            }

            this.activeModal = this.$uibModal.open(modalConfig);
            this.activeModal.result.catch(() => {}).finally(() => {
                delete this.activeModal;
            });
            return this.activeModal;
        }

        closeActiveModal(force = false) {
            if (this.activeModal && (force || !this.persistentModal)) {
                this.activeModal.dismiss();
            }
        }
    }

    app.service('modalService', ModalService);
};

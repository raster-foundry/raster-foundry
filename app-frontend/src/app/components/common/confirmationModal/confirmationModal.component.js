// Component code
import confirmationModalTpl from './confirmationModal.html';

const rfConfirmationModal = {
    templateUrl: confirmationModalTpl,
    bindings: {
        close: '&',
        dismiss: '&',
        modalInstance: '<',
        resolve: '<'
    },
    replace: true
};

export default rfConfirmationModal;

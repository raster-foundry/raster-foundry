import publishModalTpl from './publishModal.html';

const rfPublishModal = {
    templateUrl: publishModalTpl,
    bindings: {
        close: '&',
        dismiss: '&',
        modalInstance: '<',
        resolve: '<'
    }
};

export default rfPublishModal;

import publishModalTpl from './publishModal.html';

const rfPublishModal = {
    templateUrl: publishModalTpl,
    bindings: {
        close: '&',
        dismiss: '&',
        modalInstance: '<',
        resolve: '<'
    },
    controller: 'PublishModalController'
};

export default rfPublishModal;

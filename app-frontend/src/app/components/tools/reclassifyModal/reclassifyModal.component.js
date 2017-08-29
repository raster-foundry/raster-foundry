import reclassifyModalTpl from './reclassifyModal.html';

const rfReclassifyModal = {
    templateUrl: reclassifyModalTpl,
    bindings: {
        close: '&',
        dismiss: '&',
        modalInstance: '<',
        resolve: '<'
    },
    controller: 'ReclassifyModalController'
};

export default rfReclassifyModal;

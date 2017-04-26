import exportModalTpl from './exportModal.html';

const rfexportModal = {
    templateUrl: exportModalTpl,
    bindings: {
        close: '&',
        dismiss: '&',
        modalInstance: '<',
        resolve: '<'
    },
    controller: 'ExportModalController'
};

export default rfexportModal;

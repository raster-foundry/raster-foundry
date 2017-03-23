import importModalTpl from './importModal.html';

const rfImportModal = {
    templateUrl: importModalTpl,
    bindings: {
        close: '&',
        dismiss: '&',
        modalInstance: '<',
        resolve: '<'
    },
    controller: 'ImportModalController'
};

export default rfImportModal;

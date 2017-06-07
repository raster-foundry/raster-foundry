import projectExportModalTpl from './projectExportModal.html';

const rfProjectExportModal = {
    templateUrl: projectExportModalTpl,
    bindings: {
        close: '&',
        dismiss: '&',
        modalInstance: '<',
        resolve: '<'
    },
    controller: 'ProjectExportModalController'
};

export default rfProjectExportModal;

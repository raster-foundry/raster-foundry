import projectCreateModalTpl from './projectCreateModal.html';

const rfProjectCreateModal = {
    templateUrl: projectCreateModalTpl,
    bindings: {
        close: '&',
        dismiss: '&',
        modalInstance: '<',
        resolve: '<'
    },
    controller: 'ProjectCreateModalController'
};

export default rfProjectCreateModal;

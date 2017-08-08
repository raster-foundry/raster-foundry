import toolCreateModalTpl from './toolCreateModal.html';

const rfToolCreateModal = {
    templateUrl: toolCreateModalTpl,
    bindings: {
        close: '&',
        dismiss: '&',
        modalInstance: '<',
        resolve: '<'
    },
    controller: 'ToolCreateModalController'
};

export default rfToolCreateModal;

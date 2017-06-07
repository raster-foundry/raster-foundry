import projectPublishModalTpl from './projectPublishModal.html';

const rfProjectPublishModal = {
    templateUrl: projectPublishModalTpl,
    bindings: {
        close: '&',
        dismiss: '&',
        modalInstance: '<',
        resolve: '<'
    },
    controller: 'ProjectPublishModalController'
};

export default rfProjectPublishModal;

import createProjectModalTpl from './createProjectModal.html';

const rfCreateProjectModal = {
    templateUrl: createProjectModalTpl,
    bindings: {
        close: '&',
        dismiss: '&',
        modalInstance: '<',
        resolve: '<'
    },
    controller: 'CreateProjectModalController'
};

export default rfCreateProjectModal;

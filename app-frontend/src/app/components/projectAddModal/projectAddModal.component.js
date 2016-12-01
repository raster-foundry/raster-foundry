// Component code
import projectAddModalTpl from './projectAddModal.html';

const ProjectAddModal = {
    templateUrl: projectAddModalTpl,
    bindings: {
        close: '&',
        dismiss: '&',
        modalInstance: '<',
        resolve: '<'
    },
    controller: 'ProjectAddModalController'
};

export default ProjectAddModal;

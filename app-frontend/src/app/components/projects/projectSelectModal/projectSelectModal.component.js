// Component code
import projectSelectModalTpl from './projectSelectModal.html';

const ProjectSelectModalComponent = {
    templateUrl: projectSelectModalTpl,
    bindings: {
        close: '&',
        dismiss: '&',
        modalInstance: '<',
        resolve: '<'
    },
    controller: 'ProjectSelectModalController'
};

export default ProjectSelectModalComponent;

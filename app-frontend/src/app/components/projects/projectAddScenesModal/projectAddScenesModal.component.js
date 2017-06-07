// Component code
import projectAddScenesModalTpl from './projectAddScenesModal.html';

const ProjectAddScenesModalComponent = {
    templateUrl: projectAddScenesModalTpl,
    bindings: {
        close: '&',
        dismiss: '&',
        modalInstance: '<',
        resolve: '<'
    },
    controller: 'ProjectAddScenesModalController'
};

export default ProjectAddScenesModalComponent;

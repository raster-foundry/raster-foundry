// Component code
import selectProjectModalTpl from './selectProjectModal.html';

const SelectProjectModalComponent = {
    templateUrl: selectProjectModalTpl,
    bindings: {
        close: '&',
        dismiss: '&',
        modalInstance: '<',
        resolve: '<'
    },
    controller: 'SelectProjectModalController'
};

export default SelectProjectModalComponent;

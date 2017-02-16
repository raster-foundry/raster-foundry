// Component code
import selectedScenesModalTpl from './selectedScenesModal.html';

const SelectedScenesModalComponent = {
    templateUrl: selectedScenesModalTpl,
    bindings: {
        close: '&',
        dismiss: '&',
        modalInstance: '<',
        resolve: '<'
    },
    controller: 'SelectedScenesModalController'
};

export default SelectedScenesModalComponent;

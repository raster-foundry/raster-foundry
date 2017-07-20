// Component code
import mapSearchModalTpl from './mapSearchModal.html';

const MapSearchModalComponent = {
    templateUrl: mapSearchModalTpl,
    bindings: {
        close: '&',
        dismiss: '&',
        modalInstance: '<',
        resolve: '<'
    },
    controller: 'MapSearchModalController'
};

export default MapSearchModalComponent;

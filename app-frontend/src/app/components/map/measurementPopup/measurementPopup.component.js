// Component code
import measurementPopupTpl from './measurementPopup.html';

const MeasurementPopupComponent = {
    templateUrl: measurementPopupTpl,
    bindings: {
        delete: '&',
        type: '<',
        measurement: '<'
    },
    controller: 'MeasurementPopupController'
};

export default MeasurementPopupComponent;

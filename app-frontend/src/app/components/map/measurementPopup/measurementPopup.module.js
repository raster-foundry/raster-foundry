import angular from 'angular';
import MeasurementPopupComponent from './measurementPopup.component.js';
import MeasurementPopupController from './measurementPopup.controller.js';

const MeasurePopupModule = angular.module('components.map.measurementPopup', []);

MeasurePopupModule.controller(
    'MeasurementPopupController', MeasurementPopupController
);
MeasurePopupModule.component(
    'rfMeasurementPopup', MeasurementPopupComponent
);

export default MeasurePopupModule;

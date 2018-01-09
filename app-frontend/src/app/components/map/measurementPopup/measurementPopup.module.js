import angular from 'angular';
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

class MeasurementPopupController {
    $onInit() {
        this.calculateUnitsAndNumber();
        this.unitType = 'metric';
    }

    $onChanges(changes) {
        if (changes.type && changes.type.currentValue) {
            let shapeType = changes.type.currentValue;
            this.measurementType = shapeType === 'polyline' ? 'Distance ' : 'Area';
            this.calculateUnitsAndNumber();
        }

        if (changes.measurement && changes.measurement.currentValue) {
            this.measurement = changes.measurement.currentValue;
            this.calculateUnitsAndNumber();
        }
    }

    calculateUnitsAndNumber() {
        switch (this.unitType) {
        case 'imperial':
            if (this.measurementType && this.measurementType === 'Area') {
                const sqft = this.measurement * 10.7639;
                const sqMiToSqFt = 27878400;
                if (sqft > sqMiToSqFt) {
                    this.calculatedMeasurment = sqft / sqMiToSqFt;
                    this.unit = 'square miles';
                } else {
                    this.calculatedMeasurment = sqft;
                    this.unit = 'square feet';
                }
            } else {
                const feet = this.measurement * 3.28084;
                const miToFt = 5280;
                if (feet > miToFt) {
                    this.unit = 'miles';
                    this.calculatedMeasurment = feet / miToFt;
                } else {
                    this.unit = 'feet';
                    this.calculatedMeasurment = feet;
                }
            }
            break;
        default:
            let order = Math.floor(Math.log(this.measurement) / Math.LN10 + 0.000000001);
            if (this.measurementType && this.measurementType === 'Area') {
                let isLarge = order > 6;
                this.unit = isLarge ? 'square kilometers' : 'square meters';
                this.calculatedMeasurment = isLarge ?
                    this.measurement / Math.pow(10, 6) :
                    this.measurement;
            } else {
                let isLarge = order > 3;
                this.unit = isLarge ? 'kilometers' : 'meters';
                this.calculatedMeasurment = isLarge ?
                    this.measurement / Math.pow(10, 3) :
                    this.measurement;
            }
        }
    }

    toggleUnits() {
        this.unitType = this.unitType === 'metric' ? 'imperial' : 'metric';
        this.calculateUnitsAndNumber();
    }
}

const MeasurePopupModule = angular.module('components.map.measurementPopup', []);

MeasurePopupModule.controller(
    'MeasurementPopupController', MeasurementPopupController
);
MeasurePopupModule.component(
    'rfMeasurementPopup', MeasurementPopupComponent
);

export default MeasurePopupModule;

export default class MeasurementPopupController {
    $onInit() {
        this.calculateUnitsAndNumber();
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

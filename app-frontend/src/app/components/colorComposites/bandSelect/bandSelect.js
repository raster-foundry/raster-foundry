import angular from 'angular';
import bandSelectTpl from './bandSelect.html';

const rfBandSelect = {
    templateUrl: bandSelectTpl,
    controller: 'BandSelectController',
    bindings: {
        selectedBand: '<',
        bands: '<',
        onBandSelect: '&',
        disabled: '<'
    }
};

class BandSelectController {

    get activeBand() {
        return `${this.selectedBand}`;
    }

    set activeBand(index) {
        this.onBandSelect({ index: +index });
    }
}

const BandSelectModule = angular.module('components.bandSelect', []);
BandSelectModule.component('rfBandSelect', rfBandSelect);
BandSelectModule.controller('BandSelectController', BandSelectController);

export default BandSelectModule;

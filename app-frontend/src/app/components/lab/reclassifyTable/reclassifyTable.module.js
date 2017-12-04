import angular from 'angular';
import reclassifyTableTpl from './reclassifyTable.html';

const ReclassifyTableComponent = {
    templateUrl: reclassifyTableTpl,
    bindings: {
        classifications: '<',
        onClassificationsChange: '&',
        onBreaksChange: '&',
        onAllEntriesValidChange: '&',
        onNoGapsOverlapsChange: '&'
    },
    controller: 'ReclassifyTableController'
};

class ReclassifyTableController {
    constructor(reclassifyService) {
        'ngInject';
        this.reclassifyService = reclassifyService;
    }

    $onInit() {
        this.enteredRanges = this.breaksToInternal(this.classifications);
        this.invalidCount = 0;
        this.noGapsOverlaps = true;
    }

    $onChanges(changesObj) {
        if (changesObj.classifications && changesObj.classifications.currentValue) {
            this.enteredRanges = this.breaksToInternal(this.classifications);
        }
    }

    /**
      * Convert a maps of breaks -> values to our internal representation.
      *
      * The expected incoming format is an Object of the form:
      * { "1": 10, "3": 11, "6": 13 }, where both the keys and the values can be Numbers.
      *
      * The output is an array of arrays which represent the mappings of the object in pairs.
      * Each pair looks like this: [{start: Number, end: Number}, Number]
      *
      * @param {Object} breaksMap mapping of histogram breaks to output values
      * @returns {Array} array of [range, value] pairs.
      */
    breaksToInternal(breaksMap) {
        // Keys converted into numbers and then range objects.
        // The range object conversion also sorts.
        let ranges = this.reclassifyService.breaksToRangeObjects(
            Object.keys(breaksMap).map((brk) => Number(brk))
        );
        // Values in order by key
        let values = Object.entries(breaksMap).sort(
            (pair1, pair2) => Number(pair1[0]) - Number(pair2[0])
        ).map((pair) => pair[1]);
        // Zip them together
        return ranges.map((range, index) => [range, values[index]]);
    }

    internalToBreaks(internalRep) {
        let rangeValuePairs = angular.copy(internalRep);
        // Assume that the range representation as a whole is valid
        rangeValuePairs.sort((pair1, pair2) => pair1[0].start - pair2[0].start);
        return rangeValuePairs.map(p => p[1]);
    }

    updateEntryRange(range, id) {
        this.enteredRanges[id][0] = range;
        if (range) {
            this.classificationUpdateIfValid();
        }
    }

    updateEntryValue(value, id) {
        this.enteredRanges[id][1] = value;
        if (value) {
            this.classificationUpdateIfValid();
        }
    }

    classificationUpdateIfValid() {
        if (this.invalidCount === 0) {
            this.checkGapsOverlaps();
            if (this.noGapsOverlaps) {
                this.onClassificationsChange({
                    breakpoints: this.internalToBreaks(this.enteredRanges)
                });
            }
        }
    }

    updateBreak(breakpoint, index) {
        this.classifications[index] = breakpoint;
        this.onBreaksChange({breakpoints: this.classifications});
    }

    trackInvalidEntries(validity, count = 1) {
        let oldCount = this.invalidCount;
        if (validity === true) {
            this.invalidCount -= count;
        } else {
            this.invalidCount += count;
        }
        // Detect a change
        if (oldCount === 0 || this.invalidCount === 0) {
            this.onAllEntriesValidChange({allEntriesValid: this.invalidCount === 0});
        }
    }

    checkGapsOverlaps() {
        let oldNoGapsOverlaps = this.noGapsOverlaps;
        this.noGapsOverlaps = this.reclassifyService.noGapsOrOverlaps(
            this.enteredRanges.map((r) => r[0])
        );
        if (oldNoGapsOverlaps !== this.noGapsOverlaps) {
            this.onNoGapsOverlapsChange({noGapsOverlaps: this.noGapsOverlaps});
        }
    }
}

const ReclassifyTableModule = angular.module('components.lab.reclassifyTable', []);

ReclassifyTableModule.controller('ReclassifyTableController', ReclassifyTableController);
ReclassifyTableModule.component('rfReclassifyTable', ReclassifyTableComponent);

export default ReclassifyTableModule;

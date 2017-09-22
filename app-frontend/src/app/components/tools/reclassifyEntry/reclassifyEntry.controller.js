export default class ReclassifyEntryController {
    constructor(reclassifyService) {
        'ngInject';
        this.reclassifyService = reclassifyService;
    }

    $onInit() {
        this._classRange = this.classRange;
        this._classValue = this.classValue;
        this.rangeValid = this._classRange !== null;
        this.valueValid = this._classValue !== null;
        // A nonexistent entry is valid, so if we're just coming into existence with invalid
        // values, we need to notify of a change.
        if (!this.isValid()) {
            this.onValidityChange({validity: false});
        }
    }

    isValid() {
        return this.rangeValid && this.valueValid;
    }

    // A nonexistent entry is valid, so we need to notify of a change when we're destroyed if we're
    // not already valid.
    $onDestroy() {
        if (!this.isValid()) {
            this.onValidityChange({validity: true});
        }
    }

    get classValueEntry() {
        if (!this._classValueEntry && this._classValue !== null) {
            return this._classValue;
        }
        return this._classValueEntry;
    }

    set classValueEntry(newValEntry) {
        let wasValid = this.isValid();
        if (newValEntry !== null) {
            this.valueValid = true;
            this._classValue = this.reclassifyService.valueFromString(newValEntry);
        } else {
            this.valueValid = false;
            this._classValue = newValEntry;
        }
        // Check for validity changes
        if (this.isValid() !== wasValid) {
            this.onValidityChange({validity: this.isValid()});
        }
        this._classValueEntry = newValEntry;
    }

    get classRangeEntry() {
        if (!this._classRangeEntry && this._classRange) {
            return this.reclassifyService.rangeObjectToString(this._classRange);
        }
        return this._classRangeEntry;
    }

    set classRangeEntry(newEntry) {
        let wasValid = this.isValid();
        // Valid
        if (newEntry) {
            this.rangeValid = true;
            this._classRange = this.reclassifyService.rangeObjectFromString(newEntry);
        // Invalid
        } else {
            this.rangeValid = false;
            this._classRange = newEntry;
        }
        // Only fire on changes in validity
        if (this.isValid() !== wasValid) {
            this.onValidityChange({validity: this.isValid()});
        }
        // Always store the entry because similar entries will map to the same range, e.g.
        // 0.0 and 0.000, so we always display whatever the user has typed, not the normalized
        // formatting.
        this._classRangeEntry = newEntry;
    }
}

export default class ReclassifyModalController {
    constructor(reclassifyService) {
        'ngInject';
        this.reclassifyService = reclassifyService;
        this.classifications = this.resolve.classifications;
    }

    $onInit() {
        // Give the table a new object to break update cycles
        this.displayClassifications = angular.copy(this.classifications);
        this.noGapsOverlaps = true;
        this.allEntriesValid = true;
        this.classCount = Object.keys(this.classifications).length;
    }

    updateClassCount(newClassCount) {
        if (!newClassCount || newClassCount === Object.keys(this.classifications).length) {
            // Don't do anything on invalid numbers or if the number hasn't changed
            return;
        }
        if (newClassCount > Object.keys(this.classifications).length) {
            // Add more empty classification slots
            let breaksToAdd = newClassCount - Object.keys(this.classifications).length;
            let lastBreak = Object.keys(this.classifications).sort((a, b) => a - b).slice(-1)[0];
            // Fill them with values above the highest break
            // TODO: This needs to be made smarter; right now we don't have any way of knowing what
            // the maximum allowable value in our histograms is.
            let newBreaks = Array(breaksToAdd).fill(1).map(
                (_, index) => index + 1 + Number(lastBreak)
            );
            newBreaks.forEach(function (classBreak) {
                this.classifications[classBreak] = 'NODATA';
            }, this);
            // Copy over to the table
            this.displayClassifications = angular.copy(this.classifications);
        } else if (newClassCount < Object.keys(this.classifications).length) {
            // Lop off classification slots from the end
            let orderedBreaks = Object.keys(this.classifications).sort((a, b) => a - b);
            let breaksToRemove = orderedBreaks.slice(
                newClassCount - Object.keys(this.classifications).length
            );
            breaksToRemove.forEach(function (classBreak) {
                delete this.classifications[classBreak];
            }, this);
            // Copy to table
            this.displayClassifications = angular.copy(this.classifications);
        }
    }

    equalInterval() {
        // TODO: We should be using the histogram max/min because the entered max/min aren't
        // guaranteed to cover the whole range of the available histogram values. This should be
        // changed once we add histogram functionality.
        let orderedKeys = Object.keys(this.classifications).sort((a, b) => a - b);
        let min = Math.min(...orderedKeys);
        let max = Math.max(...orderedKeys);
        let rangeWidth = (max - min) / this.classCount;
        // Keep each range's associated output value so that output values stay in the same
        // order when applying the equal interval, but overwrite the range values.
        let newClassifications = {};
        orderedKeys.forEach(function (classBreak, index) {
            newClassifications[rangeWidth * (index + 1)] = this.classifications[classBreak];
        }, this);
        this.classifications = newClassifications;
        this.displayClassifications = angular.copy(this.classifications);
    }
}

/* global _ */
export default class ReclassifyModalController {
    constructor(reclassifyService, toolService) {
        'ngInject';
        this.reclassifyService = reclassifyService;
        this.toolService = toolService;
        this.classifications = this.resolve.classifications;
        this.child = this.resolve.child;
        this.model = this.resolve.model;
    }

    $onInit() {
        // Give the table a new object to break update cycles
        this.displayClassifications = angular.copy(this.classifications);
        this.noGapsOverlaps = true;
        this.allEntriesValid = true;
        this.classCount = Object.keys(this.classifications).length;
        this.isLoadingHistogram = false;
    }

    $onChanges() {
        this.initHistogram();
    }

    initHistogram() {
        this.isLoadingHistogram = true;
        if (this.model.get('toolrun')) {
            this.toolService
                .getNodeHistogram(this.model.get('toolrun').id, this.child.id)
                .then((histogram) => {
                    this.histogram = histogram;
                }).finally(() => {
                    this.isLoadingHistogram = false;
                });
        }
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
            this.classifications = angular.copy(this.classifications);
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
            this.classifications = angular.copy(this.classifications);
            this.displayClassifications = angular.copy(this.classifications);
        }
    }

    equalInterval() {
        if (this.histogram) {
            const orderedKeys = Object.keys(this.classifications).sort((a, b) => a - b);
            const min = this.histogram.minimum;
            const max = this.histogram.maximum;
            const rangeWidth = (max - min) / this.classCount;
            // Keep each range's associated output value so that output values stay in the same
            // order when applying the equal interval, but overwrite the range values.
            const newClassifications = orderedKeys.reduce((acc, c, idx) => {
                acc[rangeWidth * (idx + 1)] = this.classifications[c];
                return acc;
            }, {});
            this.classifications = newClassifications;
            this.displayClassifications = angular.copy(this.classifications);
        }
    }

    onClassificationsChange(breakpoints) {
        const orderedKeys = Object.keys(this.classifications).sort((a, b) => a - b).map(k => +k);
        const breakpointValues = breakpoints.map(b => b.value).sort((a, b) => a - b).map(k => +k);
        const updateRequired = !_.isEqual(orderedKeys, breakpointValues);

        if (updateRequired) {
            const newClassifications = orderedKeys.reduce((acc, c, idx) => {
                acc[breakpointValues[idx]] = this.classifications[c];
                return acc;
            }, {});
            this.classifications = newClassifications;
            this.displayClassifications = angular.copy(this.classifications);
        }
    }
}

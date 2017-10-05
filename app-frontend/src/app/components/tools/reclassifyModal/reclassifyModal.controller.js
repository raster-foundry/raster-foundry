/* global _ */
export default class ReclassifyModalController {
    constructor(reclassifyService, toolService) {
        'ngInject';
        this.reclassifyService = reclassifyService;
        this.toolService = toolService;
        this.breaks = this.resolve.breaks;
        this.child = this.resolve.child;
        this.model = this.resolve.model;
    }

    $onInit() {
        this.noGapsOverlaps = true;
        this.allEntriesValid = true;
        this.isLoadingHistogram = false;

        // Give the table a new object to break update cycles
        this.updateBreaks(this.breaks);
    }

    $onChanges() {
        this.initHistogram();
    }

    get classCount() {
        if (this.breaks) {
            return this.breaks.length;
        }
        return 0;
    }

    set classCount(newClassCount) {
        if (!newClassCount || newClassCount === this.classCount) {
            // Don't do anything on invalid numbers or if the number hasn't changed
            return;
        }
        if (newClassCount > this.classCount) {
            // Add more empty classification slots
            let breaksToAdd = newClassCount - this.classCount;
            let lastBreak = this.breaks.slice(-1)[0];

            // Scale the step size to the histogram range if possible
            let step = this.histogram ?
                Math.pow(10,
                    Math.round(
                        Math.log10((this.histogram.maximum - this.histogram.minimum) / 100)
                    )
                ) :
                1;

            // Fill them with values above the highest break
            let newBreaks = Array(breaksToAdd).fill(1).map(
                (_, index) => (index + 1) * step + Number(lastBreak.break)
            );

            this.updateBreaks([
                ...this.breaks,
                ...newBreaks.map(b => {
                    return {
                        break: b,
                        start: lastBreak.break,
                        value: 'NODATA'
                    };
                })
            ]);
        } else if (newClassCount < this.classCount) {
            this.updateBreaks(this.breaks.slice(0, newClassCount));
        }
    }

    initHistogram() {
        this.isLoadingHistogram = true;
        if (this.model.get('toolrun') && this.child) {
            this.toolService
                .getNodeHistogram(this.model.get('toolrun').id, this.child.id)
                .then((histogram) => {
                    this.histogram = histogram;
                }).finally(() => {
                    this.isLoadingHistogram = false;
                });
        } else {
            this.isLoadingHistogram = false;
        }
    }

    equalInterval() {
        if (this.histogram) {
            const min = this.histogram.minimum;
            const max = this.histogram.maximum;
            const rangeWidth = (max - min) / this.classCount;

            // Keep each range's associated output value so that output values stay in the same
            // order when applying the equal interval, but overwrite the range values.
            this.updateBreaks(this.breaks.map((b, i) => {
                return Object.assign({}, b, {
                    break: rangeWidth * (i + 1)
                });
            }));
        }
    }

    updateBreaks(breaks) {
        const alignedBreaks = this.reclassifyService.alignBreakpoints(breaks);
        if (!_.isEqual(alignedBreaks, this.breaks)) {
            this.breaks = angular.copy(alignedBreaks);
            this.displayClassifications = angular.copy(alignedBreaks);
        }
    }

    closeAndUpdate() {
        this.close({$value: this.breaks});
    }
}

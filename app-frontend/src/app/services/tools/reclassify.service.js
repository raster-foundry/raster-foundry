export default (app) => {
    class ReclassifyService {
        constructor() {
            'ngInject';
        }
        /**
          * Converts a list of range breakpoints into range objects
          * @param {array} breaksList list of breakpoint numbers
          * @returns {array} list of range objects
          */
        breaksToRangeObjects(breaksList) {
            return breaksList.sort((a, b) => a - b).map((breakVal, index, array) => {
                if (index === 0) {
                    return {start: 0, end: breakVal};
                }
                return {start: array[index - 1], end: breakVal};
            });
        }

        /**
          * Verifies that a list of range objects has no gaps or overlaps
          *
          * Note that this does not include single-valued ranges currently.
          *
          * @param {array} ranges Array of range objects ({start: Number, end: Number}) to validate
          * @returns {boolean} Whether the input ranges are contiguous (no gaps) without overlapping
          */
        noGapsOrOverlaps(ranges) {
            // TODO: Combining single-value ranges with multi-value ranges is hard, so this doesn't
            // try; single-value ranges are simply skipped.
            // This validates by sorting ranges and then checking that there are no gaps or
            // overlaps, which simply means that the endpoint of range n should be the same as the
            // startpoint of range n+1 (my understanding is that our ranges are exclusive at the
            // start and inclusive at the end).
            let checkRanges = angular.copy(ranges)
                .sort((r1, r2) => r1.start - r2.start)
                .filter((range) => range.start !== range.end);
            // Check for gaps / overlaps to the right.
            let gapOrOverlap = checkRanges.map((rangeObj, index, array) => {
                if (index === array.length - 1) {
                    return false;
                }
                return rangeObj.end !== array[index + 1].start;
            });
            if (gapOrOverlap.filter((bool) => bool).length > 0) {
                return false;
            }
            return true;
        }
        /**
         * Adjusts breakpoint start values to remove any 'gaps'.
         *
         * @param {array} breakpoints an array of breakpoint objects { start, break, value}
         * @returns {array} an array of breakpoints where there are no gaps
         * @memberof ReclassifyService
         */
        alignBreakpoints(breakpoints) {
            return breakpoints.map((b, i, breaks) => {
                const prev = breaks[i - 1];
                // eslint-disable-next-line eqeqeq
                const start = prev != null ? +prev.break : 'Min';
                return {
                    break: b.break,
                    value: b.value,
                    start
                };
            });
        }
    }
    app.service('reclassifyService', ReclassifyService);
};

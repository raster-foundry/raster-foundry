export default (app) => {
    class ReclassifyService {
        constructor() {
            'ngInject';

            // Rather than trying to construct a giant complicated RegEx, do two for the two cases
            // we expect.
            // These regexes aren't perfect; they don't account for strings like '01', so we have
            // to do a second pass with Number later.
            // Matches e.g. '1 - 10' or '1.5 - 10.0'
            this.rangeMatcher = /^\s*(\d+\.?\d*)\s*-\s*(\d+\.?\d*)\s*$/;
            // Matches e.g. '1' or '1.5'
            this.singleMatcher = /^\s*(\d+\.?\d*)\s*$/;
        }

       /**
         * Validates whether a range string is valid
         *
         * @param {string} rangeStr string to validate
         * @returns {boolean} whether the string represents a valid range
         */
        isRangeStrValid(rangeStr) {
            let rangeNumbers = this.numerifyRange(this.unpackRange(rangeStr));
            return rangeNumbers !== null && rangeNumbers[1] >= rangeNumbers[0];
        }

        /**
          * Creates a range object from a valid range string
          *
          * @param {string} rangeStr string to construct an object from
          * @returns {object} An object representing the range, or null if rangeStr is invalid.
          */
        rangeObjectFromString(rangeStr) {
            let rangeNumbers = this.numerifyRange(this.unpackRange(rangeStr));
            return {
                start: rangeNumbers[0],
                end: rangeNumbers[1]
            };
        }

        /**
          * The inverse of the above; creates a string from a range object
          *
          * @param {object} rangeObj Range object to serialize to a string
          * @returns {string} string representation of the range, as 'start - end'
          */
        rangeObjectToString(rangeObj) {
            if (rangeObj.start === rangeObj.end) {
                return `${rangeObj.start}`;
            }
            return `${rangeObj.start} - ${rangeObj.end}`;
        }

        /**
          * Checks whether a classification value string is valid
          *
          * @param {string} valueStr string to validate
          * @returns {boolean} whether the string is a number or 'NODATA'
          */
        isValueStrValid(valueStr) {
            return !isNaN(Number(valueStr)) || valueStr === 'NODATA';
        }

        /**
          * Creates a range object from a valid range string
          *
          * @param {string} valueStr string to construct a value from
          * @returns {number} A number representing the value, or 'NODATA',
          *                   or null if valueStr is invalid.
          */
        valueFromString(valueStr) {
            // TODO: This has a weird return type since it is String / Number; it would be better
            // to return as kind of a tagged union e.g. define an enum like this:
            // { NUMBER: Symbol('number'), NODATA: Symbol('nodata') }, and then
            // return values such as { type: NUMBER, value: 2.0 } or { type: NODATA }
            // but that would add complexity for not much apparent benefit right now.
            if (!isNaN(Number(valueStr))) {
                return Number(valueStr);
            } else if (valueStr === 'NODATA') {
                return valueStr;
            }
            return null;
        }

        /**
          * Unpacks a potential range object
          *
          * @param {string} rangeStr string to unpack into range start / end
          * @returns {array} List of range start/end strings or null if they could not be found
          */
        unpackRange(rangeStr) {
            let twoValueRange = this.rangeMatcher.exec(rangeStr);
            if (twoValueRange !== null) {
                return [twoValueRange[1], twoValueRange[2]];
            }
            let singleValueRange = this.singleMatcher.exec(rangeStr);
            if (singleValueRange !== null) {
                return [singleValueRange[1], singleValueRange[1]];
            }
            return null;
        }

        /**
          * Convert a pair of range bounds strings
          *
          * @param {array} rangeStartEnd array of string to unpack into range start / end
          * @returns {array} List of range start/end strings or null if one/both are NaN
          */
        numerifyRange(rangeStartEnd) {
            if (rangeStartEnd === null) {
                return null;
            }
            let rangeNumbers = rangeStartEnd.map((str) => Number(str));
            let nans = rangeNumbers.filter((num) => isNaN(num));
            if (nans.length > 0) {
                return null;
            }
            return rangeNumbers;
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
          * Equality check for ranges
          *
          * @param {object} range1 First range to compare
          * @param {object} range2 Second range to compare
          * @returns {boolean} whether range1 and range2 have the same start/end.
          *                    Null / undefined ranges are never equal
          */
        rangesEqual(range1, range2) {
            if (range1 && range2) {
                return range1.start === range2.start && range1.end === range2.end;
            }
            return false;
        }
    }
    app.service('reclassifyService', ReclassifyService);
};

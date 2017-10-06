/* globals _ */

const {
    colorSchemes: defaultColorSchemes,
    colorSchemeTypes: defaultColorSchemeTypes,
    colorBlendModes: defaultColorBlendModes
} = require('./colorScheme.defaults.json');

export default (app) => {
    class ColorSchemeService {
        constructor() {
            this.defaultColorSchemes = defaultColorSchemes;
            this.defaultColorSchemeTypes = defaultColorSchemeTypes;
            this.defaultColorBlendModes = defaultColorBlendModes;
        }

        // (colors:string[], bitDepth:int) => { int: string }
        // colors are expected in css hex style (#FFFFFF)
        colorsToDiscreteScheme(colors, bitDepth = 8) {
            if (colors && colors.length > 1) {
                const spacing = Math.floor((Math.pow(2, bitDepth) - 1) / colors.length - 1);
                return colors.reduce((acc, color, index) => {
                    acc[index * spacing] = `${color}FF`;
                    return acc;
                }, {});
            }
            return false;
        }

        colorsToSequentialScheme(colors) {
            if (colors && colors.length > 1) {
                return colors.reduce((acc, color, index) => {
                    acc[index] = `${color}FF`;
                    return acc;
                }, {});
            }
            return false;
        }

        schemeFromBreaksAndColors(breaks, colors) {
            return breaks.reduce((acc, b, i) => {
                acc[b] = colors[i];
                return acc;
            }, {});
        }

        toBinnedColors(colors) {
            const numColors = colors.length;
            return _.zip(
                colors.map((color, index) => `${color} ${index / numColors * 100}%`),
                colors.map((color, index) => `${color} ${(index + 1) / numColors * 100}%`)
            ).join(', ');
        }

        /**
          * Locate the color scheme matching a set of single-band-options from the API
          *
          * @param {object} singleBandOptions - A SingleBandOptions object from the API
          * @returns {object} - The color scheme matching the object, or the first one to
          *                     match the datatype, if a perfect match can't be found.
          */
        matchSingleBandOptions(singleBandOptions) {
            let targetScheme = angular.copy(singleBandOptions.colorScheme);
            if (singleBandOptions.reversed) {
                targetScheme.reverse();
            }
            let found = this.defaultColorSchemes.find(
                scheme => scheme.type === singleBandOptions.dataType &&
                    _.isEqual(
                        this.colorStopsToProportionalArray(scheme.colors),
                        targetScheme
                    )
            );
            // If can't find, pick the first with a matching data type
            if (!found) {
                found = this.defaultColorSchemes.find(
                    scheme => scheme.type === singleBandOptions.dataType
                );
            }
            return found;
        }

        /**
          * Convert a stop and color to a fragment of a CSS gradient
          *
          * @param {number} stop - Percentage stop (0.0 - 1.0)
          * @param {string} color - Color formatted as #RRGGBB
          * @param {boolean} reverse - Whether the ramp should be reversed.
          *
          * @returns {string} - CSS gradient fragment e.g. '#AABBCC 25%'
          */
        stopAndColorToCSS(stop, color, reverse) {
            if (reverse) {
                return `${color} ${100 * (1 - stop)}%`;
            }
            return `${color} ${100 * stop}%`;
        }

        /**
          * Converts an object of stop: color pairs to an array with repeated colors.
          * Note that this won't give perfect results for all possible color schemes,
          * but it enables us to generate a pretty good approximation of the color scheme without
          * knowing anything about the histogram of the image that we're applying it to.
          *
          * @param {object} colors - Mapping of percentage stops to color strings
          * @param {boolean} reversed - Whether the gradient should be returned in reverse order
          *
          * @returns {array} - List of colors with elements possibly repeated in order to match
          *                    the percentage distribution specified by the stops object.
          *                    For example, {'0.5': '#000000', '0.75': '#888888', '1.0': '#FFFFFF'}
          *                    would become ['#000000', '#000000', '#888888', '#FFFFFF'].
          */
        colorStopsToProportionalArray(colors, reversed) {
            const orderedStops = Object.keys(colors).sort((a, b) => Number(a) - Number(b));
            const stopWidths = orderedStops.map((stop, index, stops) => {
                if (index === 0) {
                    return Number(stop);
                }
                return Number(stop) - Number(stops[index - 1]);
            });
            if (Number(orderedStops[0]) === 0) {
                // remove first stop since it has no width if it's 0
                stopWidths.splice(0, 1);
            }
            const minWidth = Math.min(...stopWidths);
            // Normalize smallest width to 1 and then round to head off floating point issues
            const integerWidths = stopWidths.map(width => Math.round(1.0 / minWidth * width));
            let colorsArray = [];
            integerWidths.forEach((width, index) => {
                const stop = orderedStops[index];
                const color = colors[stop];
                let i = 0;
                while (width - i > 0) {
                    colorsArray.push(color);
                    i += 1;
                }
            });
            if (reversed) {
                colorsArray.reverse();
            }
            return colorsArray;
        }

        /**
          * Applies stop percentages to a concrete range and returns a new map
          *
          * @param {object} colors - Mapping of percentage stops to color strings
          * @param {number} min - Minimum range value
          * @param {number} max - Maximum range value
          *
          * @returns {object} - New mapping with percentages mapped to the [min,max] range
          */
        colorStopsToRange(colors, min, max) {
            const span = max - min;
            let rangedColors = {};
            Object.entries(colors).forEach(function (entry) {
                const [stop, color] = entry;
                rangedColors[(Number(stop) * span + min).toString()] = color;
            });
            return rangedColors;
        }

        /**
          * Convert a color ramp into a CSS linear-gradient
          *
          * @param {object} colors - Mapping of percentage stops to colors in CSS hex format
          * @param {number} direction - Orientation of gradient, in degrees
          * @param {number} bins - Bin the gradient if > 0
          * @param {boolean} reversed - Whether to generate the gradient in reverse order
          *
          * @returns {object} A CSS-format object containing 'background: linear-gradient(...)'
          */
        colorsToBackground(colors, direction = 90, bins = 0, reversed) {
            const colorsArray = this.colorStopsToProportionalArray(colors);
            if (reversed) {
                colorsArray.reverse();
            }
            const colorString = bins > 0 ?
                this.toBinnedColors(colorsArray) : colorsArray.join(', ');
            const style = {
                background: `linear-gradient(${direction}deg, ${colorString})`
            };
            return style;
        }
    }

    app.service('colorSchemeService', ColorSchemeService);
};

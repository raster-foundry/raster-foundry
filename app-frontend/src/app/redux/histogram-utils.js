import _ from 'lodash';

const { colorSchemes: colorSchemes } = require('../services/projects/colorScheme.defaults.json');
// return an ordered array of breakpoints
export function breakpointsFromRenderDefinition(renderDefinition, idGenerator) {
    if (renderDefinition.breakpoints) {
        let breakpoints = Object.entries(renderDefinition.breakpoints)
            .map(([breakpoint, color]) => {
                let style;
                if (renderDefinition.scale === 'CATEGORICAL') {
                    style = 'arrow';
                } else {
                    style = 'hidden';
                }
                return {id: idGenerator(), value: Number(breakpoint), color, options: {
                    style, alwaysShowNumbers: false
                }};
            }).sort((a, b) => a.value - b.value);
        _.first(breakpoints).options = {
            style: 'bar',
            alwaysShowNumbers: false
        };
        _.last(breakpoints).options = {
            style: 'bar',
            alwaysShowNumbers: false
        };
        return breakpoints;
    }
    return [];
}

// construct a renderDefinition given histogram options and breakpoints
export function renderDefinitionFromState(options, breakpoints) {
    let min = options.masks.min;
    let max = options.masks.max;
    let clip;
    if (min && max) {
        clip = 'both';
    } else if (min) {
        clip = 'left';
    } else if (max) {
        clip = 'right';
    } else {
        clip = 'none';
    }

    let renderDefinition = {
        breakpoints: {},
        clip,
        scale: options.scale
    };

    breakpoints.forEach((breakpoint) => {
        renderDefinition.breakpoints['' + breakpoint.value] = breakpoint.color;
    });

    return renderDefinition;
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
export function colorStopsToRange(colors, min, max) {
    const span = max - min;
    let rangedColors = {};
    if (Array.isArray(colors)) {
        colors.map((color, index, arr) => {
            return {
                stop: index / (arr.length - 1),
                value: color
            };
        }).forEach((color) => {
            rangedColors[(color.stop * span + min).toString()] = color.value;
        });
    } else {
        Object.entries(colors).forEach(function (entry) {
            const [stop, color] = entry;
            rangedColors[(Number(stop) * span + min).toString()] = color;
        });
    }
    return rangedColors;
}

export function createRenderDefinition(histogram) {
    let min = histogram.minimum;
    let max = histogram.maximum;

    let defaultColorScheme = colorSchemes.find(
        s => s.label === 'Viridis'
    );
    let breakpoints = colorStopsToRange(defaultColorScheme.colors, min, max);
    let renderDefinition = {clip: 'none', scale: 'SEQUENTIAL', breakpoints};
    let histogramOptions = {range: {min, max}, baseScheme: {
        colorScheme: Object.entries(defaultColorScheme.colors)
            .map(([key, val]) => ({break: key, color: val}))
            .sort((a, b) => a.break - b.break)
            .map((c) => c.color),
        dataType: 'SEQUENTIAL',
        colorBins: 0
    }};

    return {
        renderDefinition,
        histogramOptions
    };
}

import tpl from './index.html';
import { get, debounce, entries, sortBy, find, filterNot } from 'lodash';

import {
    breakpointsFromRenderDefinition,
    renderDefinitionFromState,
    colorStopsToRange
} from '_redux/histogram-utils';

class QuickEditHistogramController {
    constructor($rootScope, uuid4, $q, $element, $window, $timeout, graphService) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    $onInit() {
        this.graphId = this.uuid4.generate();
        if (!this.currentUpdate && this.plot && this.renderDefinition && this.ranges) {
            this.updateGraph(this.plot, this.renderDefinition, this.ranges);
        }
        this.debouncedBreakpointChange = debounce(this.onBreakpointChange.bind(this), 500);
        this.initGraph();
        this.onWindowResizeDebounced = debounce(this.onWindowResize.bind(this), 500);
        this.$window.addEventListener('resize', this.onWindowResizeDebounced.bind(this));
        this.$timeout(this.onWindowResize.bind(this), 500);
    }

    $onDestroy() {
        if (this.onWindowResize) {
            this.$window.removeEventListener('resize', this.onWindowResizeDebounced);
        }
        this.graphService.deregister(this.graphId);
    }

    onWindowResize() {
        this.getGraph().then(graph => {
            graph.render();
        });
    }

    $onChanges(changes) {
        const plot = get(changes, 'plot.currentValue');
        const metadata = get(changes, 'metadata.currentValue');
        const renderDefinition = get(metadata, 'renderDefinition');
        if (renderDefinition) {
            this.renderDefinition = renderDefinition;
            this.breakpoints = breakpointsFromRenderDefinition(
                this.renderDefinition,
                this.uuid4.generate
            );
        }
        const histogramOptions = get(metadata, 'histogramOptions');
        if (histogramOptions) {
            this.histogramOptions = histogramOptions;
            this.options = Object.assign({}, this.options, {
                dataRange: this.histogramOptions.range,
                baseScheme: this.histogramOptions.baseScheme
            });
        }
        const ranges = get(changes, 'ranges.currentValue');
        if (plot || metadata || ranges) {
            this.updateGraph(plot, renderDefinition, ranges);
        }
    }

    getGraph() {
        if (this.graphId) {
            return this.graphService.getGraph(this.graphId);
        }
        return this.$q.reject();
    }

    initGraph() {
        this.options = {
            masks: {
                min: false,
                max: false
            },
            scale: 'SEQUENTIAL',
            height: 50,
            style: {
                height: '50px'
            },
            type: 'single'
        };
        const elem = this.$element.find('.graph-container svg')[0];
        this.graphService.register(elem, this.graphId, this.options);
    }

    onMaskChange(mask, value) {
        if (this.options) {
            if (!this.options.masks) {
                this.options.masks = {
                    min: false,
                    max: false
                };
            }
            this.options.masks[mask] = value;
        }
        if (this.plot && this.renderDefinition && this.ranges) {
            this.renderDefinition = renderDefinitionFromState(this.options, this.breakpoints);
            this.updateGraph(this.plot, this.renderDefinition, this.ranges);
            const metadata = Object.assign({}, this.metadata, {
                renderDefinition: this.renderDefinition,
                manualRenderDefinition: true
            });
            this.onChange({
                metadata
            });
        }
    }

    updateGraph(plot = this.plot, renderDefinition = this.renderDefinition, ranges = this.ranges) {
        if (!(plot && renderDefinition && ranges)) {
            return this.$q.reject();
        }
        const currentUpdate = this.getGraph().then(graph => {
            if (currentUpdate === this.currentUpdate) {
                graph
                    .setData({
                        histogram: plot,
                        breakpoints: this.breakpoints
                    })
                    .setOptions(
                        Object.assign({}, this.options, {
                            range: ranges.bufferedRange
                        })
                    )
                    .render();
            }
        });
        this.currentUpdate = currentUpdate;
        return currentUpdate;
    }

    onBreakpointChange(breakpoint, value) {
        if (breakpoint.options.style === 'bar') {
            this.breakpoints = this.rescaleInnerBreakpoints(breakpoint, value);
        } else {
            const bp = find(this.breakpoints, b => b.id === breakpoint.id);
            if (bp) {
                bp.value = value;
            } else {
                throw new Error(
                    'onBreakpointChanged called with invalid breakpoint',
                    breakpoint,
                    value
                );
            }
        }
        this.renderDefinition = renderDefinitionFromState(this.options, this.breakpoints);
        this.updateGraph(this.plot, this.renderDefinition, this.ranges);
        const updatedMetadata = Object.assign({}, this.metadata, {
            renderDefinition: this.renderDefinition,
            manualRenderDefinition: true
        });
        this.onChange({
            metadata: updatedMetadata
        });
    }

    getBreakpointRange(breakpoints) {
        return breakpoints.reduce(
            (range, current) => ({
                min: range.min < current.value ? range.min : current.value,
                max: range.max > current.value ? range.max : current.value
            }),
            {}
        );
    }

    rescaleInnerBreakpoints(breakpoint, value) {
        const minBreakpoint = this.breakpoints.reduce((minAcc, current) => {
            if (!Number.isFinite(minAcc.value)) {
                return current;
            }
            return minAcc.value < current.value ? minAcc : current;
        }, {});
        const maxBreakpoint = this.breakpoints.reduce((maxAcc, current) => {
            if (!Number.isFinite(maxAcc.value)) {
                return current;
            }
            return maxAcc.value > current.value ? maxAcc : current;
        }, {});
        const newRange = {
            min: minBreakpoint.value,
            max: maxBreakpoint.value
        };
        const oldRange = Object.assign({}, newRange);
        if (breakpoint.value > minBreakpoint.value) {
            newRange.max = value;
        } else if (breakpoint.value < maxBreakpoint.value) {
            newRange.min = value;
        }

        const absNewRange = newRange.max - newRange.min;
        const absOldRange = oldRange.max - oldRange.min;

        return this.breakpoints.map(bp => {
            if (bp.id === breakpoint.id) {
                return Object.assign({}, bp, {
                    value
                });
            } else if (bp.id === minBreakpoint.id || bp.id === maxBreakpoint.id) {
                return Object.assign({}, bp);
            }

            let percent = (bp.value - oldRange.min) / absOldRange;
            let newValue = percent * absNewRange + newRange.min;
            return Object.assign({}, bp, {
                value: newValue
            });
        });
    }

    onColorSchemeChange(baseScheme) {
        const breakpointRange = this.getBreakpointRange(this.breakpoints);
        const breakpointMap = colorStopsToRange(
            baseScheme.colorScheme,
            breakpointRange.min,
            breakpointRange.max
        );
        this.breakpoints = breakpointsFromRenderDefinition(
            Object.assign({}, this.renderDefinition, {
                breakpoints: breakpointMap
            }),
            this.uuid4.generate
        );
        this.options = Object.assign({}, this.options, {
            baseScheme: Object.assign({}, baseScheme)
        });
        this.renderDefinition = renderDefinitionFromState(this.options, this.breakpoints);
        this.updateGraph(this.plot, this.renderDefinition, this.ranges);
        const metadata = Object.assign({}, this.metadata, {
            renderDefinition: this.renderDefinition,
            histogramOptions: Object.assign({}, this.metadata.histogramOptions, this.options),
            manualRenderDefinition: true
        });
        this.onChange({
            metadata
        });
    }

    breakpointsToColorStops(breakpoints) {
        return sortBy(entries(breakpoints), ([stop, color]) => stop).map(([stop, color]) => color);
    }

    clampToData() {
        const breakpointRange = this.ranges.dataRange;
        const baseScheme =
            this.options.baseScheme || this.breakpointsToColorStops(this.breakpoints);
        const breakpointMap = colorStopsToRange(
            baseScheme.colorScheme,
            breakpointRange.min,
            breakpointRange.max
        );
        this.breakpoints = breakpointsFromRenderDefinition(
            Object.assign({}, this.renderDefinition, {
                breakpoints: breakpointMap
            }),
            this.uuid4.generate
        );
        this.renderDefinition = renderDefinitionFromState(this.options, this.breakpoints);
        this.updateGraph(this.plot, this.renderDefinition, this.ranges);
        const metadata = Object.assign({}, this.metadata, {
            renderDefinition: this.renderDefinition,
            manualRenderDefinition: true
        });
        this.onChange({
            metadata
        });
    }
}

const component = {
    bindings: {
        plot: '<',
        metadata: '<',
        ranges: '<',
        onChange: '&'
    },
    templateUrl: tpl,
    controller: QuickEditHistogramController.name
};

export default angular
    .module('components.histogram.quickEditHistogram', [])
    .controller(QuickEditHistogramController.name, QuickEditHistogramController)
    .component('rfQuickEditHistogram', component).name;

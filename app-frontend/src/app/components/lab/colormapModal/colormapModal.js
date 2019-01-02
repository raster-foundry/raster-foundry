/* globals FileReader */
import angular from 'angular';
import _ from 'lodash';
import * as d3 from 'd3';

import colormapModalTpl from './colormapModal.html';

const ColormapModalComponent = {
    templateUrl: colormapModalTpl,
    bindings: {
        close: '&',
        dismiss: '&',
        modalInstance: '<',
        resolve: '<'
    },
    controller: 'ColormapModalController'
};

class ColormapModalController {
    constructor($rootScope, $scope, $element, colorSchemeService, graphService, uuid4) {
        $rootScope.autoInject(this, arguments);
    }

    $onInit() {
        this.selectMode = 'form';
        this.$scope.$watch('$ctrl.selectMode', (mode) => {
            if (mode === 'file') {
                delete this.uploadError;
                this.$scope.$evalAsync(this.bindUploadEvent.bind(this));
            }
        });

        this.graphId = this.uuid4.generate();
        this.getGraph = () => this.graphService.getGraph(this.graphId);

        this.histMin = _.get(this.resolve, 'histogram.data.minimum', 'unknown');
        if (this.histMin === 'unknown') {
            this.minPixelValue = 0;
        } else {
            this.minPixelValue = this.histMin;
        }
        this.histMax = _.get(this.resolve, 'histogram.data.maximum', 'unknown');
        if (this.histmax === 'unknown') {
            this.maxPixelValue = 255;
        } else {
            this.maxPixelValue = this.histMax;
        }
        this.breakpoints = _.clone(this.resolve.breakpoints) || [
            {value: this.minPixelValue, color: '#ffffff'},
            {value: this.maxPixelValue, color: '#000000'}
        ];
        // this.updateHistogramColors();
        this.plot = this.resolve.plot;

        this.getGraph().then((graph) => {
            this.$scope.$watch('$ctrl.breakpoints', (breakpoints) => {
                if (breakpoints && breakpoints.length) {
                    graph.setData({histogram: this.plot, breakpoints})
                        .setOptions(this.options)
                        .render();
                } else {
                    graph.setData().render();
                }
            }, true);
        });
    }

    $postLink() {
        this.$scope.$evalAsync(() => this.initGraph());
    }

    initGraph() {
        this.options = {
            dataRange: {
                min: 0,
                max: 255
            },
            breakpointRange: {
                min: 0,
                max: 255
            },
            masks: {
                min: false,
                max: false
            },
            scale: 'SEQUENTIAL'
        };

        let elem = this.$element.find('.cm-modal-section.horizontal svg')[0];
        this.graph = this.graphService.register(elem, this.graphId, this.options);
    }


    bindUploadEvent() {
        $('#btn-upload').change((e) => {
            let upload = e.target.files[0];
            if (upload) {
                let reader = new FileReader();
                reader.onload = (event) => {
                    try {
                        delete this.uploadError;
                        const result = JSON.parse(event.target.result.replace(/'/g, '"'));
                        this.breakpoints = this.colorsToBreakpoints(result);
                        this.redistributePixelValues();
                        this.selectMode = 'form';
                        this.$scope.$evalAsync();
                    } catch (err) {
                        this.uploadError = err.message;
                        this.$scope.$evalAsync();
                    }
                };
                reader.readAsText(upload);
            }
        });
    }

    onPaste(text) {
        try {
            const result = JSON.parse(text.replace(/'/g, '"'));
            this.breakpoints = this.colorsToBreakpoints(result);
            this.selectMode = 'form';
            this.redistributePixelValues();
            this.$scope.$evalAsync();
        } catch (e) {
            this.pasteError = e.message;
            this.$scope.$evalAsync();
        }
    }

    colorsToBreakpoints(colors) {
        let colorRegex = /^#([0-9a-f]{6})$/i;
        if (colors.map) {
            return colors.map((color, index) => {
                if (colorRegex.test(color)) {
                    return {value: index, color: color};
                }
                throw new Error(
                    `${color} is not a valid hex color code. Colors must be in the form '#aaaaaa'.`
                );
            });
        }
        throw new Error(`Expected colors to be in an array. Found ${typeof colors}`);
    }

    addBreakpoint(breakpoint, index) {
        if (breakpoint && typeof index === 'number') {
            this.breakpoints.splice(index, 0, _.clone(breakpoint));
        } else {
            this.breakpoints.push(_.clone(_.last(this.breakpoints)));
        }
    }

    deleteBreakpoint(index) {
        this.breakpoints.splice(index, 1);
    }

    redistributePixelValues() {
        this.breakpoints = this.breakpoints.map((breakpoint, index) => {
            return Object.assign({}, breakpoint, {
                value: (this.maxPixelValue - this.minPixelValue) *
                    (index / (this.breakpoints.length - 1)) +
                    this.minPixelValue
            });
        });
    }

    updateHistogramColors() {
        if (!this.api.getElement) {
            return;
        }

        let colors = this.calculateHistogramColors();

        this.updateHistogramGradient(colors);
    }

    calculateHistogramColors() {
        let range = this.histMax - this.histMin;
        let data = this.breakpoints.map((bp) => {
            let offset = (bp.value - this.histMin) / range * 100;
            return {offset: offset, color: bp.color};
        }).sort((a, b) => a.offset - b.offset).map((bp) => {
            return {offset: `${bp.offset}%`, color: bp.color};
        });

        return data;
    }

    updateHistogramGradient(data) {
        let svg = d3.select(this.api.getElement().children()[0]);
        const selectedDef = svg.select('defs')[0];
        let defs = selectedDef && selectedDef.length ?
            svg.select('defs') : svg.append('defs');
        const lg = defs.selectAll('linearGradient')[0];
        let linearGradient = lg && lg.length ?
            defs.selectAll('linearGradient') : defs.append('linearGradient');

        linearGradient.attr('id', 'line-gradient-modal')
            .attr('gradientUnits', 'userSpaceOnUse')
            .attr('x1', '0%').attr('y1', 0)
            .attr('x2', '100%').attr('y2', 0)
            .selectAll('stop')
            .data(data)
            .enter().append('stop')
            .attr('offset', (d) => d.offset)
            .attr('stop-color', (d) => d.color)
            .attr('stop-opacity', (d) => Number.isFinite(d.opacity) ? d.opacity : 1.0);
        this.$scope.$evalAsync();
    }

    closeWithBreakpoints() {
        const bpMap = {};
        this.breakpoints.forEach(({value, color}) => {
            bpMap[value] = color;
        });
        this.close({$value: bpMap});
    }
}

const ColormapModalModule = angular.module('components.lab.colormapModal', []);

ColormapModalModule.controller('ColormapModalController', ColormapModalController);
ColormapModalModule.component('rfColormapModal', ColormapModalComponent);

export default ColormapModalModule;

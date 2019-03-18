import angular from 'angular';
import $ from 'jquery';
import histogramBreakpointTpl from './histogramBreakpoint.html';
import { get, debounce } from 'lodash';

const HistogramBreakpointComponent = {
    templateUrl: histogramBreakpointTpl,
    controller: 'HistogramBreakpointController',
    bindings: {
        color: '<',
        breakpoint: '<',
        range: '<',
        precision: '<',
        options: '<',
        upperBound: '<',
        lowerBound: '<',
        onBreakpointChange: '&?'
    }
};

const defaultOptions = {
    style: 'bar',
    alwaysShowNumbers: true
};

class HistogramBreakpointController {
    constructor($rootScope, $element, $scope, $log, $document) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    $onInit() {
        this.documentBody = this.$document.find('body');
        this.parent = this.$element.parent();
        this.registerEvents();
        if (!this.breakpointPosition) {
            this.breakpointPosition = '0%';
        }

        if (!this._options) {
            this._options = Object.assign({}, defaultOptions, this.options);
        }
    }

    $onChanges(changes) {
        if (changes.options && changes.options.currentValue) {
            this._options = Object.assign({}, defaultOptions, changes.options.currentValue);
            if (this.breakpoint) {
                this.setPositionFromBreakpoint(this.breakpoint);
            }
        }
        const breakpoint = get(changes, 'breakpoint.currentValue');
        const breakpointUpdated = Number.isFinite(breakpoint);
        const shouldUpdateBreakpoint =
            get(changes, 'range.currentValue') ||
            get(changes, 'precision.currentValue') ||
            breakpointUpdated;
        if (shouldUpdateBreakpoint && breakpointUpdated) {
            this.setPositionFromBreakpoint(breakpoint);
        } else if (shouldUpdateBreakpoint && Number.isFinite(this.breakpoint)) {
            this.setPositionFromBreakpoint(this.breakpoint);
        }
        const range = get(changes, 'range.currentValue');
        if (range) {
            const { precision, min, max } = range;
            this.roundedMin = Math.round((min - precision) / precision) * precision;
            this.roundedMax = Math.round((max + precision) / precision) * precision;
        }
    }

    validateBreakpoint(value) {
        let breakpoint = value;

        const areFinite = numbers => {
            return numbers.reduce((acc, val) => {
                return acc && Number.isFinite(val);
            }, true);
        };

        if (
            areFinite([this.upperBound, breakpoint, this.precision]) &&
            breakpoint > this.upperBound - this.precision
        ) {
            breakpoint = this.upperBound - this.precision;
        } else if (areFinite([breakpoint, this.upperBound]) && breakpoint > this.upperBound) {
            breakpoint = this.upperBound;
        } else if (
            areFinite([this.upperBound, breakpoint, this.precision]) &&
            breakpoint < this.lowerBound + this.precision
        ) {
            breakpoint = this.lowerBound + this.precision;
        } else if (areFinite([breakpoint, this.lowerBound]) && breakpoint < this.lowerBound) {
            breakpoint = this.lowerBound;
        }

        if (areFinite([breakpoint, this.precision])) {
            if (this.precision > 0) {
                breakpoint = Math.round(breakpoint / this.precision) * this.precision;
            } else if (this.precision === 0) {
                breakpoint = Math.round(breakpoint);
            }
        }
        return breakpoint;
    }

    setPositionFromBreakpoint(breakpoint) {
        if (
            this.range &&
            Number.isFinite(this.range.min) &&
            Number.isFinite(this.range.max) &&
            Number.isFinite(breakpoint)
        ) {
            this._breakpoint = this.validateBreakpoint(breakpoint);
            let percent =
                ((this._breakpoint - this.range.min) / (this.range.max - this.range.min)) * 100;
            percent = Math.min(Math.max(0, percent), 100);

            this.breakpointPosition = `${percent}%`;
        } else {
            this.breakpointPosition = '0%';
        }
        this.$element.css({
            left: this.breakpointPosition,
            display: 'initial'
        });
    }

    registerEvents() {
        this.$scope.$on('$destroy', this.onDestroy.bind(this));
    }

    onInputChange() {
        if (
            Number.isFinite(this._breakpoint) &&
            ['bar', 'arrow', 'point'].includes(this._options.style)
        ) {
            this.setPositionFromBreakpoint(this._breakpoint);
            this.onBreakpointChange({
                breakpoint: this._breakpoint
            });
        }
    }

    onGrabberMouseDown() {
        this.parent.addClass('dragging');
        this.dragging = true;
        this.parent.on('mousemove mouseleave', this.onMouseMove.bind(this));
        this.documentBody.on('mouseup mouseleave', this.onMouseUp.bind(this));
        this.$scope.$evalAsync();
    }

    onMouseMove(event) {
        const classList = get(event, 'target.classList') || [];
        const tagName = get(event, 'target.tagName');
        if (
            classList.contains('graph-container') ||
            ['rf-node-histogram', 'rf-reclassify-histogram'].includes('tagName')
        ) {
            event.stopPropagation();
            // this is changing depending on the zoom width, so we need to find a way around it
            let leftBound = this.parent.offset().left;
            let position = event.clientX;
            let percent = (position - leftBound) / this.parent.width();
            let breakpoint = this.validateBreakpoint(
                (this.range.max - this.range.min) * percent + this.range.min
            );

            if (this._breakpoint !== breakpoint) {
                this.setPositionFromBreakpoint(breakpoint);
                this.$scope.$evalAsync();
            }
        }
    }

    onMouseUp() {
        this.onBreakpointChange({
            breakpoint: this._breakpoint
        });
        this.documentBody.off('mouseup mouseleave');
        this.parent.off('mousemove mouseleave');
        this.parent.removeClass('dragging');
        this.dragging = false;
        this.$scope.$evalAsync();
    }

    onDestroy() {
        this.parent.removeClass('dragging');
        this.documentBody.off('mouseup mouseleave');
        this.parent.off('mousemove mouseleave');
    }
}

const HistogramBreakpointModule = angular.module('components.histogram.histgramBreakpoint', []);

HistogramBreakpointModule.component('rfHistogramBreakpoint', HistogramBreakpointComponent);
HistogramBreakpointModule.controller(
    'HistogramBreakpointController',
    HistogramBreakpointController
);

export default HistogramBreakpointModule;

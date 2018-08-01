import angular from 'angular';
import histogramBreakpointTpl from './histogramBreakpoint.html';

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
    constructor(
        $element, $scope, $log, $document
    ) {
        'ngInject';
        this.$element = $element;
        this.$document = $document;
        this.$scope = $scope;
        this.$log = $log;
    }

    $onInit() {
        this.documentBody = angular.element(this.$document[0].body);
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
        if (changes.range && changes.range.currentValue) {
            this.setPositionFromBreakpoint();
        }
        if (changes.options && changes.options.currentValue) {
            this._options = Object.assign({}, defaultOptions, changes.options.currentValue);
            this.setPositionFromBreakpoint();
        }
        if (changes.precision && changes.precision.currentValue) {
            this.setPositionFromBreakpoint();
        }
        if (changes.breakpoint && Number.isFinite(changes.breakpoint.currentValue)) {
            this.setPositionFromBreakpoint();
        }
    }

    validateBreakpoint(value) {
        let breakpoint = value;
        if (breakpoint > this.range.max) {
            breakpoint = this.range.max;
        } else if (breakpoint < this.range.min) {
            breakpoint = this.range.min;
        }

        if (this.precision && breakpoint > this.upperBound - this.precision) {
            breakpoint = this.upperBound - this.precision;
        } else if (breakpoint > this.upperBound) {
            breakpoint = this.upperBound;
        } else if (this.precision && breakpoint < this.lowerBound + this.precision) {
            breakpoint = this.lowerBound + this.precision;
        } else if (breakpoint < this.lowerBound) {
            breakpoint = this.lowerBound;
        }

        if (Number.isFinite(this.precision) && this.precision > 0) {
            breakpoint = Math.round(breakpoint / this.precision) * this.precision;
        } else if (this.precision === 0) {
            breakpoint = Math.round(breakpoint);
        }
        return breakpoint;
    }

    setPositionFromBreakpoint() {
        let hide = false;
        if (this.range &&
            Number.isFinite(this.range.min) &&
            Number.isFinite(this.range.max) &&
            Number.isFinite(this.breakpoint)
        ) {
            this._breakpoint = this.validateBreakpoint(this.breakpoint);

            let percent = (
                this._breakpoint - this.range.min
            ) / (
                this.range.max - this.range.min
            ) * 100;

            if (percent < 0) {
                percent = 0;
            }
            this.breakpointPosition = `${percent}%`;
        } else {
            this.breakpointPosition = '0%';
        }
        this.$element.css({left: this.breakpointPosition, display: hide ? 'none' : 'initial'});
    }

    registerEvents() {
        this.$scope.$on('$destroy', this.onDestroy.bind(this));
    }

    onInputChange() {
        this.setPositionFromBreakpoint();
        this.onBreakpointChange({breakpoint: this._breakpoint});
    }

    onGrabberMouseDown() {
        this.parent.addClass('dragging');
        this.dragging = true;
        this.parent.on('mousemove mouseleave', this.onMouseMove.bind(this));
        this.documentBody.on('mouseup mouseleave', this.onMouseUp.bind(this));
        this.$scope.$evalAsync();
    }

    onMouseMove(event) {
        if (event.target &&
            event.target.classList.contains('graph-container') ||
            event.target.tagName === 'NVD3' ||
            event.target.tagName === 'rf-node-histogram' ||
            event.target.tagName === 'rf-reclassify-histogram'
        ) {
            event.stopPropagation();
            // this is changing depending on the zoom width, so we need to find a way around it
            let leftBound = this.parent.offset().left;
            let position = event.clientX;
            let percent = (position - leftBound) / this.parent.width();
            let breakpoint = this.validateBreakpoint(
                (this.range.max - this.range.min) * percent + this.range.min
            );

            if (this.breakpoint !== breakpoint) {
                this.breakpoint = breakpoint;
                this.setPositionFromBreakpoint();
                this.$scope.$evalAsync();
            }
        }
    }

    onMouseUp() {
        this.onBreakpointChange({breakpoint: this.breakpoint});
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
    'HistogramBreakpointController', HistogramBreakpointController
);

export default HistogramBreakpointModule;

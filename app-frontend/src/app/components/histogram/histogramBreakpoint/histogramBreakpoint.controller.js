import angular from 'angular';

export default class HistogramBreakpointController {
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
        this.$scope.$watch('$ctrl.breakpoint', this.setPositionFromBreakpoint.bind(this));
    }

    validateBreakpoint(value) {
        let breakpoint = value;
        if (breakpoint > this.range.max) {
            breakpoint = this.range.max;
        } else if (breakpoint < this.range.min) {
            breakpoint = this.range.min;
        }

        if (Number.isInteger(this.precision) && this.precision >= 0) {
            let multiplier = Math.pow(10, this.precision);
            breakpoint = Math.round(breakpoint * multiplier) / multiplier;
        } else {
            this.$log.error(`Invalid histogram breakpoint precision: ${this.precision}`);
        }
        return breakpoint;
    }

    setPositionFromBreakpoint() {
        if (this.range &&
            Number.isFinite(this.range.min) &&
            Number.isFinite(this.range.max) &&
            Number.isFinite(this.breakpoint)) {
            this.breakpoint = this.validateBreakpoint(this.breakpoint);

            let percent = (
                this.breakpoint - this.range.min
            ) / (
                this.range.max - this.range.min
            ) * 100;
            this.breakpointPosition = `${percent}%`;
        } else {
            this.breakpointPosition = '0%';
        }
        this.$element.css({left: this.breakpointPosition});
    }

    registerEvents() {
        this.$scope.$on('$destroy', this.onDestroy.bind(this));
    }

    onGrabberMouseDown() {
        this.parent.addClass('dragging');
        this.dragging = true;
        this.parent.on('mousemove mouseleave', this.onMouseMove.bind(this));
        this.documentBody.on('mouseup mouseleave', this.onMouseUp.bind(this));
        this.$scope.$evalAsync();
    }

    onMouseMove(event) {
        if (!event.target || event.target.tagName !== 'NVD3') {
            return;
        }
        event.stopPropagation();
        let width = this.parent.width();
        let position = event.offsetX;
        let percent = position / width;
        let breakpoint = this.validateBreakpoint(
            (this.range.max - this.range.min) * percent + this.range.min
        );

        this.onBreakpointChange({breakpoint: breakpoint});
        this.$scope.$evalAsync();
    }

    onMouseUp() {
        this.documentBody.off('mouseup mouseleave');
        this.parent.off('mousemove mouseleave');
        this.parent.removeClass('dragging');
        this.dragging = false;
        this.$scope.$evalAsync();
    }

    onDestroy() {
        this.documentBody.off('mouseup mouseleave');
        this.parent.off('mousemove mouseleave');
    }
}

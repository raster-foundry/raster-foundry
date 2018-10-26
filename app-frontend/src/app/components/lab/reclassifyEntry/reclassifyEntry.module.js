import angular from 'angular';
import reclassifyEntryTpl from './reclassifyEntry.html';

const ReclassifyEntryComponent = {
    templateUrl: reclassifyEntryTpl,
    bindings: {
        classRange: '<',
        classValue: '<',
        break: '<',
        entryId: '@',
        onRangeChange: '&',
        onValueChange: '&',
        onBreakChange: '&',
        onValidityChange: '&'
    },
    controller: 'ReclassifyEntryController'
};

class ReclassifyEntryController {
    constructor($rootScope, $element, $timeout, reclassifyService) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    $onInit() {
        this.isShowingRange = true;
        // A nonexistent entry is valid, so if we're just coming into existence with invalid
        // values, we need to notify of a change.
        this.onValidityChange({validity: this.isValid()});
    }

    $onChanges() {
        if (this.break) {
            this.computeRange();
        }
    }

    computeRange() {
        this.classRange = `${this.break.start}\u00a0\u00a0-\u00a0\u00a0${this.break.break}`;
    }

    toggleRange(value) {
        // eslint-disable-next-line eqeqeq
        if (value != null) {
            this.isShowingRange = value;
        } else {
            this.isShowingRange = !this.isShowingRange;
        }
        if (!this.isShowingRange) {
            this.$timeout(() => {
                const el = this.$element.find('input').get(0);
                el.focus();
            }, 200);
        }
    }

    isValid() {
        const s = +this.break.start;
        const b = +this.break.break;
        const v = +this.break.value;
        return !isNaN(b) && !isNaN(v) && (b > s || isNaN(s));
    }

    // A nonexistent entry is valid, so we need to notify of a change when we're destroyed if we're
    // not already valid.
    $onDestroy() {
        if (!this.isValid()) {
            this.onValidityChange({validity: true});
        }
    }

    _onBreakChange() {
        this.break.break = +this.break.break;
        this.break.value = +this.break.value;
        this.computeRange();
        if (this.isValid()) {
            this.onBreakChange({break: this.break});
        }
    }
}

const ReclassifyEntryModule = angular.module('components.lab.reclassifyEntry', []);

ReclassifyEntryModule.controller('ReclassifyEntryController', ReclassifyEntryController);
ReclassifyEntryModule.component('rfReclassifyEntry', ReclassifyEntryComponent);

export default ReclassifyEntryModule;


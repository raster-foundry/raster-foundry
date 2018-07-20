/* globals _, document */
export default class DateRangePickerModalController {
    constructor($log, $scope, $timeout, moment, dateRangePickerConf) {
        'ngInject';
        this.$log = $log;
        this.$scope = $scope;
        this.$timeout = $timeout;
        this.Moment = moment;
        this.dateRangePickerConf = dateRangePickerConf;
    }

    $onInit() {
        this.pickerApi = {};
        this.range = this.range || this.resolve.config.range || {};
        this._range = {
            start: this.range.start || this.Moment(),
            end: this.range.end || this.Moment()
        };
        this.ranges = this.resolve.config.ranges || [];
        this.minDay = this.resolve.config.minDay;
        this.maxDay = this.resolve.config.maxDay;

        this.$timeout(() => {
            const ele = angular.element(document.getElementsByClassName('input-container'));
            const startInput = angular.element(ele[0].lastChild);
            const endInput = angular.element(ele[1].lastChild);
            this.setStartEndValues(startInput, endInput);
            // this.bindInputChangeEvents(startInput, endInput);
        }, 100);
    }

    setStartEndValues(startInput, endInput) {
        let startReformat = this.Moment(startInput.val(), 'MMM DD, YYYY').format('MM/DD/YYYY');
        startInput.val(startReformat);
        let endReformat = this.Moment(endInput.val(), 'MMM DD, YYYY').format('MM/DD/YYYY');
        endInput.val(endReformat);
    }

    isActivePreset(range, index) {
        return this.selectedRangeIndex === index && this.matchesSelectedRange(range);
    }

    matchesSelectedRange(range) {
        if (_.isEmpty(range.start) || _.isEmpty(range.end)) {
            return true;
        }
        return range.start.isSame(this._range.start) && range.end.isSame(this._range.end);
    }

    onPresetSelect(range, index) {
        if (!_.isEmpty(range.start) && !_.isEmpty(range.end)) {
            this._range.start = range.start;
            this._range.end = range.end;
            this.isRangeEmpty = false;
        } else {
            this.isRangeEmpty = true;
        }
        this.selectedRangeIndex = index;
    }

    getSelectedPreset() {
        if (this.selectedRangeIndex) {
            let selectedRange = this.ranges[this.selectedRangeIndex];
            if (this.matchesSelectedRange(selectedRange)) {
                return selectedRange;
            }
        }
        return false;
    }

    cancel() {
        this.closeWithData(false);
    }

    apply() {
        let data = {
            start: this.isRangeEmpty ? {} : this._range.start,
            end: this.isRangeEmpty ? {} : this._range.end
        };
        const selectedRange = this.getSelectedPreset();
        if (selectedRange) {
            data.preset = selectedRange.name;
        }
        this.closeWithData(data);
    }

    closeWithData(data) {
        this.close({$value: data});
    }
}

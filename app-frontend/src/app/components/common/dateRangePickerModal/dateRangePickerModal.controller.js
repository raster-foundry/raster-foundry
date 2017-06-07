export default class DateRangePickerModalController {
    constructor(moment, dateRangePickerConf) {
        'ngInject';
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
    }

    isActivePreset(range, index) {
        return this.selectedRangeIndex === index && this.matchesSelectedRange(range);
    }

    matchesSelectedRange(range) {
        return range.start.isSame(this._range.start) && range.end.isSame(this._range.end);
    }

    onPresetSelect(range, index) {
        this._range.start = range.start;
        this._range.end = range.end;
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
            start: this._range.start,
            end: this._range.end
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

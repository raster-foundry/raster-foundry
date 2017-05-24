export default class DatePickerModalController {
    constructor(moment, datePickerConf) {
        'ngInject';
        this.Moment = moment;
        this.datePickerConf = datePickerConf;
    }

    $onInit() {
        this.pickerApi = {};
        this.minDay = this.resolve.config.minDay;
        this.maxDay = this.resolve.config.maxDay;

        this.format = () => this.datePickerConf.format;
        this.selectedDay = this.resolve.config.selectedDay;
        this._selectedDay = this.getSelectedDay();
        this.setCalendarInterceptors();
    }

    setCalendarInterceptors() {
        this.calendarInterceptors = {
            daySelected: this.daySelected.bind(this)
        };
    }

    daySelected(day) {
        if (!day.isSame(this._selectedDay, 'day')) {
            this.pickerApi.render();
            this.value = this.Moment(day).format(this.getFormat());
            this._selectedDay = day;
            this.apply();
        } else {
            this.cancel();
        }
    }

    getSelectedDay() {
        return this.Moment(this.selectedDay || this.Moment(), this.getFormat());
    }

    getFormat() {
        return this.format() || 'MM DD, YYYY';
    }

    cancel() {
        this.closeWithData(false);
    }

    apply() {
        this.closeWithData(this._selectedDay);
    }

    closeWithData(data) {
        this.close({$value: data});
    }
}

import angular from 'angular';
import _ from 'lodash';
import daterangeFilterTpl from './daterangeFilter.html';

const daterangeFilterComponent = {
    templateUrl: daterangeFilterTpl,
    controller: 'DaterangeFilterController',
    bindings: {
        filter: '<',
        onFilterChange: '&'
    }
};


class DaterangeFilterController {
    constructor($location, modalService, moment) {
        this.$location = $location;
        this.modalService = modalService;
        this.moment = moment;
        this.selectedFilter = null;
        this.presets = [{
            name: 'Today',
            start: moment().startOf('day'),
            end: moment().endOf('day')
        }, {
            name: 'The last week',
            start: moment().startOf('day').subtract(1, 'weeks'),
            end: moment().endOf('day')
        }, {
            name: 'The last month',
            start: moment().startOf('day').subtract(1, 'months'),
            end: moment().endOf('day')
        }, {
            name: 'The last year',
            start: moment().startOf('day').subtract(1, 'years'),
            end: moment().endOf('day')
        }, {
            name: 'None',
            start: moment().startOf('day').subtract(100, 'years'),
            end: moment().endOf('day')
        }];

        let NonePreset = _.last(this.presets);
        this.datefilter = {
            start: NonePreset.start,
            end: NonePreset.end
        };
        this.dateFilterPreset = 'None';
    }

    $onChanges(changes) {
        if (changes.filter && changes.filter.currentValue) {
            const filter = changes.filter.currentValue;
            const preset = _.first(this.presets.filter((p) => p.name === filter.default));
            if (preset) {
                this.datefilter.start = preset.start;
                this.datefilter.end = preset.end;
                this.dateFilterPreset = preset.name;
            }

            const paramValues = this.$location.search();
            const start = paramValues[this.filter.params.min];
            const startMoment = this.moment(start);
            const end = paramValues[this.filter.params.max];
            const endMoment = this.moment(end);
            this.usingUTC = true;

            if (start && startMoment.isValid()) {
                this.dateFilterPreset = null;
                if (this.onDatetimeBorder(startMoment)) {
                    startMoment.utc().utcOffset(this.moment().utcOffset(), true).local();
                } else {
                    this.usingUTC = false;
                }
                this.datefilter.start = startMoment;
            }
            if (end && endMoment.isValid()) {
                this.dateFilterPreset = null;
                if (!this.onDatetimeBorder(endMoment)) {
                    this.usingUTC = false;
                } else {
                    endMoment.utc().utcOffset(this.moment().utcOffset(), true).local();
                }
                this.datefilter.end = endMoment;
            }

            this.setDateRange(
                this.datefilter.start,
                this.datefilter.end,
                this.dateFilterPreset
            );
        }
    }

    onDatetimeBorder(m) {
        const start = m.clone().utc().startOf('day');
        const end = m.clone().utc().endOf('day');
        return m.isSame(start) || m.isSame(end);
    }

    openDateRangePickerModal() {
        this.modalService.open({
            component: 'rfDateRangePickerModal',
            resolve: {
                config: () => Object({
                    range: this.datefilter,
                    ranges: this.presets
                })
            }
        }).result.then((range) => {
            if (range) {
                this.lastRange = range;
                this.setDateRange(range.start, range.end, range.preset);
            }
        });
    }

    setDateRange(start, end, preset) {
        this.lastSetting = {start, end, preset};
        if (_.isEmpty({start}) || _.isEmpty(end)) {
            this.clearDateFilter(false);
        } else {
            this.datefilter.start = start.startOf('day');
            this.datefilter.end = end.endOf('day');
            this.datefilterPreset = preset || false;
            this.hasDatetimeFilter = true;
            const filterParams = {};
            const min = start.clone();
            const max = end.clone();
            if (this.usingUTC) {
                min.utcOffset('+00:00', true).startOf('day');
                max.utcOffset('+00:00', true).endOf('day');
            }
            if (preset === 'None') {
                filterParams[this.filter.params.min] = null;
                filterParams[this.filter.params.max] = null;
            } else {
                filterParams[this.filter.params.min] = min.toISOString();
                filterParams[this.filter.params.max] = max.toISOString();
            }
            this.onFilterChange({
                filter: this.filter,
                filterParams
            });
        }
    }

    clearDateFilter(isResetAll) {
        this.datefilterPreset = 'None';
        this.hasDatetimeFilter = false;
        if (!isResetAll) {
            this.onFilterChange({
                minAcquisitionDatetime: null,
                maxAcquisitionDatetime: null
            });
        }
    }
    onSetUTC(value) {
        this.usingUTC = value;
        let {start, end, preset} = this.lastSetting;
        this.setDateRange(start, end, preset);
    }
}

const DaterangeFilterModule = angular.module('components.filters.daterangeFilter', []);

DaterangeFilterModule.component('rfDaterangeFilter', daterangeFilterComponent);
DaterangeFilterModule.controller('DaterangeFilterController', DaterangeFilterController);

export default DaterangeFilterModule;

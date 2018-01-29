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

            if (start && startMoment.isValid()) {
                this.datefilter.start = startMoment;
                this.dateFilterPreset = null;
            }
            if (end && endMoment.isValid()) {
                this.datefilter.end = endMoment;
                this.dateFilterPreset = null;
            }

            this.setDateRange(
                this.datefilter.start.startOf('day'),
                this.datefilter.end.endOf('day'),
                this.dateFilterPreset
            );
        }
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
                this.setDateRange(range.start, range.end, range.preset);
            }
        });
    }

    setDateRange(start, end, preset) {
        if (_.isEmpty({start}) || _.isEmpty(end)) {
            this.clearDateFilter(false);
        } else {
            this.datefilter.start = start.startOf('day');
            this.datefilter.end = end.endOf('day');
            this.datefilterPreset = preset || false;
            this.hasDatetimeFilter = true;
            const filterParams = {};
            filterParams[this.filter.params.min] = start.toISOString();
            filterParams[this.filter.params.max] = end.toISOString();
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
}

const DaterangeFilterModule = angular.module('components.filters.daterangeFilter', []);

DaterangeFilterModule.component('rfDaterangeFilter', daterangeFilterComponent);
DaterangeFilterModule.controller('DaterangeFilterController', DaterangeFilterController);

export default DaterangeFilterModule;

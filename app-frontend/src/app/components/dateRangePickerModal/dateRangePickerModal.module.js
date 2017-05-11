import angular from 'angular';
import DateRangePickerModalComponent from './dateRangePickerModal.component.js';
import DateRangePickerModalController from './dateRangePickerModal.controller.js';
require('./dateRangePickerModal.scss');

const DateRangePickerModalModule = angular.module('components.dateRangePickerModal', []);

DateRangePickerModalModule.controller('DateRangePickerModalController', DateRangePickerModalController);
DateRangePickerModalModule.component('rfDateRangePickerModal', DateRangePickerModalComponent);

export default DateRangePickerModalModule;

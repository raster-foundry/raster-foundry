/* eslint-disable */
import angular from 'angular';
import DateRangePickerModalComponent from './dateRangePickerModal.component.js';
import DateRangePickerModalController from './dateRangePickerModal.controller.js';

const DateRangePickerModalModule = angular.module('components.common.dateRangePickerModal', []);

DateRangePickerModalModule.controller('DateRangePickerModalController', DateRangePickerModalController);
DateRangePickerModalModule.component('rfDateRangePickerModal', DateRangePickerModalComponent);

export default DateRangePickerModalModule;

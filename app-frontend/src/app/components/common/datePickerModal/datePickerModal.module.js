import angular from 'angular';
import DatePickerModalComponent from './datePickerModal.component.js';
import DatePickerModalController from './datePickerModal.controller.js';

const DatePickerModalModule = angular.module('components.common.datePickerModal', []);

DatePickerModalModule.controller('DatePickerModalController', DatePickerModalController);
DatePickerModalModule.component('rfDatePickerModal', DatePickerModalComponent);

export default DatePickerModalModule;

import angular from 'angular';
import DatePickerModalComponent from './datePickerModal.component.js';
import DatePickerModalController from './datePickerModal.controller.js';
require('./datePickerModal.scss');

const DatePickerModalModule = angular.module('components.datePickerModal', []);

DatePickerModalModule.controller('DatePickerModalController', DatePickerModalController);
DatePickerModalModule.component('rfDatePickerModal', DatePickerModalComponent);

export default DatePickerModalModule;

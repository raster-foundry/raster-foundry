import angular from 'angular';
import SelectToolModalComponent from './selectToolModal.component.js';
import SelectToolModalController from './selectToolModal.controller.js';
require('../../../assets/font/fontello/css/fontello.css');

const SelectToolModalModule = angular.module('components.selectToolModal', []);

SelectToolModalModule.controller(
    'SelectToolModalController', SelectToolModalController
);
SelectToolModalModule.component(
    'rfSelectToolModal', SelectToolModalComponent
);

export default SelectToolModalModule;

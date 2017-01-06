import angular from 'angular';
import SelectProjectModalComponent from './selectProjectModal.component.js';
import SelectProjectModalController from './selectProjectModal.controller.js';
require('../../../assets/font/fontello/css/fontello.css');

const SelectProjectModalModule = angular.module('components.selectProjectModal', []);

SelectProjectModalModule.controller(
    'SelectProjectModalController', SelectProjectModalController
);
SelectProjectModalModule.component(
    'rfSelectProjectModal', SelectProjectModalComponent
);

export default SelectProjectModalModule;

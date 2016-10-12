import angular from 'angular';
import SelectedScenesModalComponent from './selectedScenesModal.component.js';
import SelectedScenesModalController from './selectedScenesModal.controller.js';
require('../../../assets/font/fontello/css/fontello.css');

const SelectedScenesModalModule = angular.module('components.selectedScenesModal', []);

SelectedScenesModalModule.controller(
    'SelectedScenesModalController', SelectedScenesModalController
);
SelectedScenesModalModule.component(
    'rfSelectedScenesModal', SelectedScenesModalComponent
);

export default SelectedScenesModalModule;

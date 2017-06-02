import angular from 'angular';
import ToggleOWComponent from './toggle-ow.component.js';
import ToggleOWController from './toggle-ow.controller.js';

const ToggleOWModule = angular.module('components.toggle-ow', []);

ToggleOWModule.component('rfToggleOw', ToggleOWComponent);
ToggleOWModule.controller('ToggleOWController', ToggleOWController);

export default ToggleOWModule;

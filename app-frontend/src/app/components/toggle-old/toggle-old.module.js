import angular from 'angular';
import ToggleComponent from './toggle-old.component.js';
import ToggleController from './toggle-old.controller.js';

const ToggleModule = angular.module('components.toggle-old', []);

ToggleModule.component('rfToggleOld', ToggleComponent);
ToggleModule.controller('ToggleOldController', ToggleController);

export default ToggleModule;

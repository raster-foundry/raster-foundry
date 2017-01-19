import angular from 'angular';
import ToggleComponent from './toggle.component.js';
import ToggleController from './toggle.controller.js';

const ToggleModule = angular.module('components.toggle', []);

require('./toggle.scss');

ToggleModule.component('rfToggle', ToggleComponent);
ToggleModule.controller('ToggleController', ToggleController);

export default ToggleModule;

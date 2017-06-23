import angular from 'angular';
import DrawToolbarComponent from './drawToolbar.component.js';
import DrawToolbarController from './drawToolbar.controller.js';
require('./drawToolbar.scss');

const DrawToolbarModule = angular.module('components.map.drawToolbar', []);

DrawToolbarModule.component('rfDrawToolbar', DrawToolbarComponent);
DrawToolbarModule.controller('DrawToolbarController', DrawToolbarController);

export default DrawToolbarModule;

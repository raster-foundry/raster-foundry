import angular from 'angular';
import DrawAoiController from './draw-aoi.controller.js';
require('./draw-aoi.scss');

const DrawAoiModule = angular.module('page.projects.edit.aoi-parameters.draw-aoi', []);

DrawAoiModule.controller('DrawAoiController', DrawAoiController);

export default DrawAoiModule;

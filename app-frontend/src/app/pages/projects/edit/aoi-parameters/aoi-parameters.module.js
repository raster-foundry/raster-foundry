import angular from 'angular';
import AOIParametersController from './aoi-parameters.controller.js';

const AOIParametersModule = angular.module('page.projects.edit.aoi-parameters', []);

AOIParametersModule.controller('AOIParametersController', AOIParametersController);

export default AOIParametersModule;

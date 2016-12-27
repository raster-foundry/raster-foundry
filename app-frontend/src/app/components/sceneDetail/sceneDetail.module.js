import angular from 'angular';
import SceneDetailComponent from './sceneDetail.component.js';
import SceneDetailComponentController from './sceneDetail.controller.js';

require('../../../assets/font/fontello/css/fontello.css');

const SceneDetailModule = angular.module('components.sceneDetail', []);

SceneDetailModule.component('rfSceneDetail', SceneDetailComponent);
SceneDetailModule.controller('SceneDetailComponentController', SceneDetailComponentController);

export default SceneDetailModule;

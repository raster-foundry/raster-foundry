import angular from 'angular';
import SceneDetailComponent from './sceneDetail.component.js';
import SceneDetailController from './sceneDetail.controller.js';
require('../../../assets/font/fontello/css/fontello.css');

const SceneDetailModule = angular.module('components.sceneDetail', []);

SceneDetailModule.component('rfSceneDetail', SceneDetailComponent);
SceneDetailModule.controller('RfSceneDetailController', SceneDetailController);

export default SceneDetailModule;

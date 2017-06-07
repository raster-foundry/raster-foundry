import angular from 'angular';
import SceneDetailComponent from './sceneDetail.component.js';
import SceneDetailComponentController from './sceneDetail.controller.js';

const SceneDetailModule = angular.module('components.scenes.sceneDetail', []);

SceneDetailModule.component('rfSceneDetail', SceneDetailComponent);
SceneDetailModule.controller('SceneDetailComponentController', SceneDetailComponentController);

export default SceneDetailModule;

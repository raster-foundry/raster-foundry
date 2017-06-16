import angular from 'angular';
import SceneListComponent from './sceneList.component.js';
import SceneListController from './sceneList.controller.js';

const SceneListModule = angular.module('components.scenes.sceneList', []);

SceneListModule.component('rfSceneList', SceneListComponent);
SceneListModule.controller('SceneListController', SceneListController);

export default SceneListModule;


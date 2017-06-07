import angular from 'angular';
import SceneDetailModalComponent from './sceneDetailModal.component.js';
import SceneDetailModalController from './sceneDetailModal.controller.js';

const SceneDetailModalModule = angular.module('components.scenes.sceneDetailModal', []);

SceneDetailModalModule.controller('SceneDetailModalController', SceneDetailModalController);
SceneDetailModalModule.component('rfSceneDetailModal', SceneDetailModalComponent);

export default SceneDetailModalModule;

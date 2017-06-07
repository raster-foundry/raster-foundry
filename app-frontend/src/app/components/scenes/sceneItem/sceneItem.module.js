import angular from 'angular';
import SceneItemComponent from './sceneItem.component.js';
import SceneItemController from './sceneItem.controller.js';

const SceneItemModule = angular.module('components.scenes.sceneItem', []);

SceneItemModule.component('rfSceneItem', SceneItemComponent);
SceneItemModule.controller('SceneItemController', SceneItemController);

export default SceneItemModule;

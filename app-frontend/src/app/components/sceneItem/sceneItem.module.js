import angular from 'angular';
import SceneItemComponent from './sceneItem.component.js';
import SceneItemController from './sceneItem.controller.js';
require('../../../assets/font/fontello/css/fontello.css');

const SceneItemModule = angular.module('components.sceneItem', []);

SceneItemModule.component('rfSceneItem', SceneItemComponent);
SceneItemModule.controller('SceneItemController', SceneItemController);

export default SceneItemModule;

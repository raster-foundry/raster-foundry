import angular from 'angular';
import slider from 'angularjs-slider';

import SceneFilterPaneComponent from './sceneFilterPane.component.js';
import SceneFilterPaneController from './sceneFilterPane.controller.js';

const SceneFilterPaneModule = angular.module('components.scenes.sceneFilterPane', [slider]);

SceneFilterPaneModule.component('rfSceneFilterPane', SceneFilterPaneComponent);
SceneFilterPaneModule.controller('SceneFilterPaneController', SceneFilterPaneController);

export default SceneFilterPaneModule;

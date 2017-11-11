import angular from 'angular';
import slider from 'angularjs-slider';
import typeahead from 'angular-ui-bootstrap/src/typeahead';

import SceneFilterPaneComponent from './sceneFilterPane.component.js';
import SceneFilterPaneController from './sceneFilterPane.controller.js';

const SceneFilterPaneModule = angular.module('components.scenes.sceneFilterPane',
    [slider, typeahead]);

SceneFilterPaneModule.component('rfSceneFilterPane', SceneFilterPaneComponent);
SceneFilterPaneModule.controller('SceneFilterPaneController', SceneFilterPaneController);

export default SceneFilterPaneModule;

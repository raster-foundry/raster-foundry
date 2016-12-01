import angular from 'angular';

import ColorCorrectScenes from './colorCorrectScenes.component.js';
import ColorCorrectScenesController from './colorCorrectScenes.controller.js';

const ColorCorrectScenesModule = angular.module('components.colorCorrectScenes', []);

ColorCorrectScenesModule.component('rfColorCorrectScenes', ColorCorrectScenes);
ColorCorrectScenesModule.controller('ColorCorrectScenesController', ColorCorrectScenesController);

export default ColorCorrectScenesModule;

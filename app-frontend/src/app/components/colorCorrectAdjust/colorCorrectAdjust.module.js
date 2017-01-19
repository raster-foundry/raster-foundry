import angular from 'angular';
import slider from 'angularjs-slider';

import ColorCorrectAdjust from './colorCorrectAdjust.component.js';
import ColorCorrectAdjustController from './colorCorrectAdjust.controller.js';

require('./colorCorrectAdjust.scss');

const ColorCorrectAdjustModule = angular.module('components.colorCorrectAdjust', [slider]);

ColorCorrectAdjustModule.component('rfColorCorrectAdjust', ColorCorrectAdjust);
ColorCorrectAdjustModule.controller('ColorCorrectAdjustController', ColorCorrectAdjustController);

export default ColorCorrectAdjustModule;

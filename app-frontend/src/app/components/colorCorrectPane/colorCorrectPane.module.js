import angular from 'angular';
import slider from 'angularjs-slider';

import ColorCorrectPane from './colorCorrectPane.component.js';
import ColorCorrectController from './colorCorrectPane.controller.js';

const ColorCorrectModule = angular.module('components.colorCorrectPane', [slider]);

ColorCorrectModule.component('colorCorrectPane', ColorCorrectPane);
ColorCorrectModule.controller('ColorCorrectController', ColorCorrectController);

export default ColorCorrectModule;

import angular from 'angular';

import ColorCorrectPane from './colorCorrectPane.component.js';
import ColorCorrectPaneController from './colorCorrectPane.controller.js';

require('./colorCorrectPane.scss');

const ColorCorrectPaneModule = angular.module('components.colorCorrectPane', []);

ColorCorrectPaneModule.component('rfColorCorrectPane', ColorCorrectPane);
ColorCorrectPaneModule.controller('ColorCorrectPaneController', ColorCorrectPaneController);

export default ColorCorrectPaneModule;

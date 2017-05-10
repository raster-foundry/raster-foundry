import angular from 'angular';
import slider from 'angularjs-slider';
require('../../../assets/font/fontello/css/fontello.css');

import AOIFilterPaneComponent from './aoiFilterPane.component.js';
import AOIFilterPaneController from './aoiFilterPane.controller.js';

const AOIFilterPaneModule = angular.module('components.AOIFilterPane', [slider]);

AOIFilterPaneModule.component('rfAoiFilterPane', AOIFilterPaneComponent);
AOIFilterPaneModule.controller('AOIFilterPaneController', AOIFilterPaneController);

export default AOIFilterPaneModule;

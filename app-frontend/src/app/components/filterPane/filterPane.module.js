import angular from 'angular';
import slider from 'angularjs-slider';
require('../../../assets/font/fontello/css/fontello.css');

import FilterPanComponent from './filterPane.component.js';
import FilterPaneController from './filterPane.controller.js';

const FilterPaneModule = angular.module('components.filterPane', [slider]);

FilterPaneModule.component('rfFilterPane', FilterPanComponent);
FilterPaneModule.controller('FilterPaneController', FilterPaneController);

export default FilterPaneModule;

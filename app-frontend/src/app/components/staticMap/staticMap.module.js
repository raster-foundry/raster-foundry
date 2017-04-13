import angular from 'angular';
import StaticMapComponent from './staticMap.component.js';
import StaticMapController from './staticMap.controller.js';

require('leaflet/dist/leaflet.css');
require('./staticMap.scss');

const StaticMapModule = angular.module('components.staticMap', []);

StaticMapModule.component('rfStaticMap', StaticMapComponent);
StaticMapModule.controller('StaticMapController', StaticMapController);

export default StaticMapModule;

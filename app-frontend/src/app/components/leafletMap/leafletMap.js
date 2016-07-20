// Entry file. Place all dependencies here
import angular from 'angular';
import leafletMap from './leafletMap.component.js';
import LeafletMapController from './leafletMap.controller.js';
require('./leafletMap.scss');

export default angular.module('app.components.leafletMap', [])
    .component('rfLeafletMap', leafletMap)
    .controller('LeafletMapController', LeafletMapController)
    .name;

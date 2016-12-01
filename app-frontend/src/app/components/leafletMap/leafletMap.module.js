import angular from 'angular';
import LeafletMapComponent from './leafletMap.component.js';
import LeafletMapController from './leafletMap.controller.js';
require('leaflet/dist/leaflet.css');
require('./leafletMap.scss');

const LeafletMapModule = angular.module('components.leafletMap', []);

LeafletMapModule.component('rfLeafletMap', LeafletMapComponent);
LeafletMapModule.controller('LeafletMapController', LeafletMapController);

export default LeafletMapModule;

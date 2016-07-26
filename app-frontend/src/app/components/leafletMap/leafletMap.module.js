import angular from 'angular';
import LeafletMapComponent from './leafletMap.component.js';
import LeafletMapController from './leafletMap.controller.js';
require('../../../assets/css/leaflet@1.0.0-rc.2.css');
require('./leafletMap.scss');

const LeafletMapModule = angular.module('components.leafletMap', []);

LeafletMapModule.component('rfLeafletMap', LeafletMapComponent);
LeafletMapModule.controller('LeafletMapController', LeafletMapController);

export default LeafletMapModule;

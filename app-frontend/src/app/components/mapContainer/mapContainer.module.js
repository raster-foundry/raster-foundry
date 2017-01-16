import angular from 'angular';
import MapContainerComponent from './mapContainer.component.js';
import MapContainerController from './mapContainer.controller.js';

require('leaflet/dist/leaflet.css');
require('leaflet-draw/dist/leaflet.draw.css');
require('leaflet-draw/dist/leaflet.draw.js');
require('./leaflet-side-by-side.js');
require('./mapContainer.scss');

const MapContainerModule = angular.module('components.mapContainer', []);

MapContainerModule.component('rfMapContainer', MapContainerComponent);
MapContainerModule.controller('MapContainerController', MapContainerController);

export default MapContainerModule;

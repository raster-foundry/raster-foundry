import angular from 'angular';
import LabMapComponent from './labMap.component.js';
import LabMapController from './labMap.controller.js';
require('./frame.module.js');

require('./labMap.scss');

require('leaflet-draw/dist/leaflet.draw.css');
require('leaflet-draw/dist/leaflet.draw.js');

const LabMapModule = angular.module('components.map.labMap', []);

LabMapModule.component('rfLabMap', LabMapComponent);
LabMapModule.controller('LabMapController', LabMapController);

export default LabMapModule;

import angular from 'angular';
import MapSearchModalComponent from './mapSearchModal.component.js';
import MapSearchModalController from './mapSearchModal.controller.js';

const MapSearchModalModule = angular.module('components.map.mapSearchModal', []);

MapSearchModalModule.controller(
    'MapSearchModalController', MapSearchModalController
);
MapSearchModalModule.component(
    'rfMapSearchModal', MapSearchModalComponent
);

export default MapSearchModalModule;

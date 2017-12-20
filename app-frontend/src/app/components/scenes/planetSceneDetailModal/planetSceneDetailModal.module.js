import angular from 'angular';
import PlanetSceneDetailModalComponent from './planetSceneDetailModal.component.js';
import PlanetSceneDetailModalController from './planetSceneDetailModal.controller.js';

const PlanetSceneDetailModalModule = angular.module('components.scenes.planetSceneDetailModal', []);

PlanetSceneDetailModalModule.controller(
  'PlanetSceneDetailModalController',
  PlanetSceneDetailModalController
);
PlanetSceneDetailModalModule.component(
  'rfPlanetSceneDetailModal',
  PlanetSceneDetailModalComponent
);

export default PlanetSceneDetailModalModule;

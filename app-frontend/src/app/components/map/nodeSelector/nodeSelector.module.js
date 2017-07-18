import angular from 'angular';
import NodeSelectorComponent from './nodeSelector.component.js';
import NodeSelectorController from './nodeSelector.controller.js';

const NodeSelectorModule = angular.module('components.map.nodeSelector', []);

NodeSelectorModule.component('rfNodeSelector', NodeSelectorComponent);
NodeSelectorModule.controller('NodeSelectorController', NodeSelectorController);

export default NodeSelectorModule;

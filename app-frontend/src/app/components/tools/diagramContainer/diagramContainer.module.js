import angular from 'angular';
import DiagramContainerComponent from './diagramContainer.component.js';
import DiagramContainerController from './diagramContainer.controller.js';

const DiagramContainerModule = angular.module('components.tools.diagramContainer', []);

DiagramContainerModule.component('rfDiagramContainer', DiagramContainerComponent);
DiagramContainerModule.controller('DiagramContainerController', DiagramContainerController);

export default DiagramContainerModule;

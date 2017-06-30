import angular from 'angular';
import DiagramContainerComponent from './diagramContainer.component.js';
import DiagramContainerController from './diagramContainer.controller.js';

const DiagramContainerModule = angular.module('components.tools.diagramContainer2', []);

DiagramContainerModule.component('rfDiagramContainer2', DiagramContainerComponent);
DiagramContainerModule.controller('DiagramContainerController2', DiagramContainerController);

export default DiagramContainerModule;

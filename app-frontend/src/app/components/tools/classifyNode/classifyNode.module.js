import angular from 'angular';
import ClassifyNodeComponent from './classifyNode.component.js';
import ClassifyNodeController from './classifyNode.controller.js';

const ClassifyNodeModule = angular.module('components.tools.classifyNode', []);

ClassifyNodeModule.component('rfClassifyNode', ClassifyNodeComponent);
ClassifyNodeModule.controller('ClassifyNodeController', ClassifyNodeController);

export default ClassifyNodeModule;

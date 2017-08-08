import angular from 'angular';
import ConstantNodeComponent from './constantNode.component.js';
import ConstantNodeController from './constantNode.controller.js';

const ConstantNodeModule = angular.module('components.tools.constantNode', []);

ConstantNodeModule.component('rfConstantNode', ConstantNodeComponent);
ConstantNodeModule.controller('ConstantNodeController', ConstantNodeController);

export default ConstantNodeModule;

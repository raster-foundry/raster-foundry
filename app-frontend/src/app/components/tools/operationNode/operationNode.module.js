import angular from 'angular';
import OperationNodeComponent from './operationNode.component.js';
import OperationNodeController from './operationNode.controller.js';

const OperationNodeModule = angular.module(
    'components.tools.operationNode', []
);

OperationNodeModule.component('rfOperationNode', OperationNodeComponent);
OperationNodeModule.controller('OperationNodeController', OperationNodeController);

export default OperationNodeModule;

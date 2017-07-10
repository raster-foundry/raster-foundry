import angular from 'angular';
import InputNodeComponent from './inputNode.component.js';
import InputNodeController from './inputNode.controller.js';

const InputNodeModule = angular.module(
    'components.tools.inputNode', []
);

InputNodeModule.component('rfInputNode', InputNodeComponent);
InputNodeModule.controller('InputNodeController', InputNodeController);

export default InputNodeModule;

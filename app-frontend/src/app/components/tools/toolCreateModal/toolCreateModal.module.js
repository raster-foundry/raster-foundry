import angular from 'angular';
import ToolCreateModalComponent from './toolCreateModal.component.js';
import ToolCreateModalController from './toolCreateModal.controller.js';

const ToolCreateModalModule = angular.module('components.tools.toolCreateModal', []);

ToolCreateModalModule.controller('ToolCreateModalController', ToolCreateModalController);
ToolCreateModalModule.component('rfToolCreateModal', ToolCreateModalComponent);

export default ToolCreateModalModule;

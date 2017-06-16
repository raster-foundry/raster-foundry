import angular from 'angular';
import ToolItemComponent from './toolItem.component.js';
import ToolItemController from './toolItem.controller.js';

const ToolItemModule = angular.module('components.tools.toolItem', []);

ToolItemModule.component('rfToolItem', ToolItemComponent);
ToolItemModule.controller('ToolItemController', ToolItemController);

export default ToolItemModule;

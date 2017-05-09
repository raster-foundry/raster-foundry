import angular from 'angular';
import StatusTagComponent from './statusTag.component.js';
import StatusTagController from './statusTag.controller.js';

require('./statusTag.scss');

const StatusTagModule = angular.module('components.statusTag', []);

StatusTagModule.component('rfStatusTag', StatusTagComponent);
StatusTagModule.controller('StatusTagController', StatusTagController);

export default StatusTagModule;

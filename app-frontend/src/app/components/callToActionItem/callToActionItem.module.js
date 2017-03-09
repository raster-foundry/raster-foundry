import angular from 'angular';
import CallToActionItemComponent from './callToActionItem.component.js';
import CallToActionItemController from './callToActionItem.controller.js';
require('./callToAction.scss');

const CallToActionItemModule = angular.module('components.callToActionItem', []);

CallToActionItemModule.controller('CallToActionItemController', CallToActionItemController);
CallToActionItemModule.component('rfCallToActionItem', CallToActionItemComponent);

export default CallToActionItemModule;

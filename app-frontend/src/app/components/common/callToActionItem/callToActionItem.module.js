import angular from 'angular';
import CallToActionItemComponent from './callToActionItem.component.js';
import CallToActionItemController from './callToActionItem.controller.js';

const CallToActionItemModule = angular.module('components.common.callToActionItem', []);

CallToActionItemModule.controller('CallToActionItemController', CallToActionItemController);
CallToActionItemModule.component('rfCallToActionItem', CallToActionItemComponent);

export default CallToActionItemModule;

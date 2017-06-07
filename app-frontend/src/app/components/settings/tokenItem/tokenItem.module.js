import angular from 'angular';
import TokenItemComponent from './tokenItem.component.js';
import TokenItemController from './tokenItem.controller.js';

const TokenItemModule = angular.module('components.settings.tokenItem', []);

TokenItemModule.component('rfTokenItem', TokenItemComponent);
TokenItemModule.controller('TokenItemController', TokenItemController);

export default TokenItemModule;

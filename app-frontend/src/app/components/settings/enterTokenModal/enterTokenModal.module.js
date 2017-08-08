import angular from 'angular';
import EnterTokenModalComponent from './enterTokenModal.component.js';
import EnterTokenModalController from './enterTokenModal.controller.js';

const EnterTokenModalModule = angular.module('components.settings.enterTokenModal', []);

EnterTokenModalModule.controller('EnterTokenModalController', EnterTokenModalController);
EnterTokenModalModule.component('rfEnterTokenModal', EnterTokenModalComponent);

export default EnterTokenModalModule;

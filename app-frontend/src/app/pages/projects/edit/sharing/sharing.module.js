import angular from 'angular';
import SharingController from './sharing.controller.js';

const SharingModule = angular.module('page.projects.edit.sharing', []);

SharingModule.controller('SharingController', SharingController);

export default SharingModule;

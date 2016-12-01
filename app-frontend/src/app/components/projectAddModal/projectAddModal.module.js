require('../../../assets/font/fontello/css/fontello.css');

import angular from 'angular';
import ProjectAddModalComponent from './projectAddModal.component.js';
import ProjectAddModalController from './projectAddModal.controller.js';

const ProjectAddModalModule = angular.module('components.projectAddModal', []);

ProjectAddModalModule.controller('ProjectAddModalController', ProjectAddModalController);
ProjectAddModalModule.component('rfProjectAddModal', ProjectAddModalComponent);

export default ProjectAddModalModule;

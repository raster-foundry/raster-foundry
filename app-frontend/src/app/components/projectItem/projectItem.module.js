import angular from 'angular';
import ProjectItemComponent from './projectItem.component.js';
import ProjectItemController from './projectItem.controller.js';
require('../../../assets/font/fontello/css/fontello.css');

const ProjectItemModule = angular.module('components.projectItem', []);

ProjectItemModule.controller('ProjectItemController', ProjectItemController);
ProjectItemModule.component('rfProjectItem', ProjectItemComponent);

export default ProjectItemModule;

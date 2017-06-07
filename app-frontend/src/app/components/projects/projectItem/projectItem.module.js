import angular from 'angular';
import ProjectItemComponent from './projectItem.component.js';
import ProjectItemController from './projectItem.controller.js';

const ProjectItemModule = angular.module('components.projects.projectItem', []);

ProjectItemModule.controller('ProjectItemController', ProjectItemController);
ProjectItemModule.component('rfProjectItem', ProjectItemComponent);

export default ProjectItemModule;

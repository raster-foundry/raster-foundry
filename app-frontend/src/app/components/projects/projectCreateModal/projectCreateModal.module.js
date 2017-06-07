import angular from 'angular';
import ProjectCreateModalComponent from './projectCreateModal.component.js';
import ProjectCreateModalController from './projectCreateModal.controller.js';

const ProjectCreateModalModule = angular.module('components.projects.projectCreateModal', []);

ProjectCreateModalModule.controller('ProjectCreateModalController', ProjectCreateModalController);
ProjectCreateModalModule.component('rfProjectCreateModal', ProjectCreateModalComponent);

export default ProjectCreateModalModule;

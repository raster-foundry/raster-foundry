import angular from 'angular';
import ProjectSelectModalComponent from './projectSelectModal.component.js';
import ProjectSelectModalController from './projectSelectModal.controller.js';

const ProjectSelectModalModule = angular.module('components.projects.projectSelectModal', []);

ProjectSelectModalModule.controller(
    'ProjectSelectModalController', ProjectSelectModalController
);
ProjectSelectModalModule.component(
    'rfProjectSelectModal', ProjectSelectModalComponent
);

export default ProjectSelectModalModule;

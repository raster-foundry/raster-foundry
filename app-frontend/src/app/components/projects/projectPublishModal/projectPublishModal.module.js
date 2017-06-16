import angular from 'angular';
import ProjectPublishModalComponent from './projectPublishModal.component.js';
import ProjectPublishModalController from './projectPublishModal.controller.js';

const ProjectPublishModalModule = angular.module('components.projects.projectPublishModal', []);

ProjectPublishModalModule.controller(
    'ProjectPublishModalController', ProjectPublishModalController
);

ProjectPublishModalModule.component(
    'rfProjectPublishModal', ProjectPublishModalComponent
);

export default ProjectPublishModalModule;

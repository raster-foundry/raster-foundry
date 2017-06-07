import angular from 'angular';
import ProjectAddScenesModalComponent from './projectAddScenesModal.component.js';
import ProjectAddScenesModalController from './projectAddScenesModal.controller.js';

const ProjectAddScenesModalModule = angular.module('components.projects.projectAddScenesModal', []);

ProjectAddScenesModalModule.controller(
    'ProjectAddScenesModalController', ProjectAddScenesModalController
);
ProjectAddScenesModalModule.component(
    'rfProjectAddScenesModal', ProjectAddScenesModalComponent
);

export default ProjectAddScenesModalModule;

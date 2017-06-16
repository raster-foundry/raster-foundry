import angular from 'angular';
import ProjectExportModalComponent from './projectExportModal.component.js';
import ProjectExportModalController from './projectExportModal.controller.js';

const ProjectExportModalModule = angular.module('components.projects.projectExportModal', []);

ProjectExportModalModule.controller(
    'ProjectExportModalController', ProjectExportModalController
);
ProjectExportModalModule.component(
    'rfProjectExportModal', ProjectExportModalComponent
);

export default ProjectExportModalModule;

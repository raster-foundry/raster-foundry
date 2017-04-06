import angular from 'angular';
import ProjectsMaskingController from './masking.controller.js';

const ProjectsMaskingModule = angular.module('pages.projects.edit.masking', []);

ProjectsMaskingModule.controller(
    'ProjectsMaskingController', ProjectsMaskingController
);

export default ProjectsMaskingModule;

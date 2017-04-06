import angular from 'angular';
import ProjectsAddScenesController from './addscenes.controller.js';

const ProjectsAddScenesModule = angular.module('pages.projects.edit.addscenes', []);

ProjectsAddScenesModule.controller(
    'ProjectsAddScenesController', ProjectsAddScenesController
);

export default ProjectsAddScenesModule;

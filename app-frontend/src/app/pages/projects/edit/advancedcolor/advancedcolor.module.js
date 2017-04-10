import angular from 'angular';
import ProjectsAdvancedColorController from './advancedcolor.controller.js';

const ProjectsAdvancedColorModule = angular.module('pages.projects.edit.advancedcolor', []);

ProjectsAdvancedColorModule.controller(
    'ProjectsAdvancedColorController', ProjectsAdvancedColorController
);

export default ProjectsAdvancedColorModule;

import angular from 'angular';
import ProjectsEditColorAdjustController from './adjust.controller.js';

const ProjectsEditColorAdjustModule = angular.module('pages.projects.edit.color.adjust', []);

ProjectsEditColorAdjustModule.controller(
    'ProjectsEditColorAdjustController', ProjectsEditColorAdjustController
);

export default ProjectsEditColorAdjustModule;

import angular from 'angular';
import ProjectsColorAdjustController from './adjust.controller.js';

const ProjectsColorAdjustModule = angular.module('pages.projects.edit.advancedcolor.adjust', []);

ProjectsColorAdjustModule.controller(
    'ProjectsColorAdjustController', ProjectsColorAdjustController
);

export default ProjectsColorAdjustModule;

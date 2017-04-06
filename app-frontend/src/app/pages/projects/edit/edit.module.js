import angular from 'angular';
import ProjectsEditController from './edit.controller.js';

const ProjectsEditModule = angular.module('pages.projects.edit', []);

ProjectsEditModule.controller('ProjectsEditController', ProjectsEditController);

export default ProjectsEditModule;

import angular from 'angular';
import ProjectsEditColorController from './color.controller.js';

const ProjectsEditColorModule = angular.module('pages.projects.edit.color', []);

ProjectsEditColorModule.controller('ProjectsEditColorController', ProjectsEditColorController);

export default ProjectsEditColorModule;

import angular from 'angular';
import ProjectsSceneBrowserController from './browse.controller.js';

const ProjectsSceneBrowserModule = angular.module('pages.projects.edit.browse', []);

ProjectsSceneBrowserModule.controller(
    'ProjectsSceneBrowserController', ProjectsSceneBrowserController
);

export default ProjectsSceneBrowserModule;

import angular from 'angular';
import ProjectsEditColormodeController from './colormode.controller.js';

const ProjectsEditColormodeModule = angular.module('pages.projects.edit.colormode', []);

ProjectsEditColormodeModule.controller(
    'ProjectsEditColormodeController', ProjectsEditColormodeController
);

export default ProjectsEditColormodeModule;

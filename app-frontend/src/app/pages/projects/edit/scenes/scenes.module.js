import angular from 'angular';
import ProjectsScenesController from './scenes.controller.js';

const ProjectsScenesModule = angular.module('pages.projects.edit.scenes', []);

ProjectsScenesModule.controller(
    'ProjectsScenesController', ProjectsScenesController
);

export default ProjectsScenesModule;

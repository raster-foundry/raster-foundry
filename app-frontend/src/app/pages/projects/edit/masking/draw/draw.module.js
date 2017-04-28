import angular from 'angular';
import ProjectsMaskingDrawController from './draw.controller.js';

const ProjectsMaskingDrawModule = angular.module('pages.projects.edit.masking.draw', []);

ProjectsMaskingDrawModule.controller(
    'ProjectsMaskingDrawController', ProjectsMaskingDrawController
);

export default ProjectsMaskingDrawModule;

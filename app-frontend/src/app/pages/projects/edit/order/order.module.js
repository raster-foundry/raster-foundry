import angular from 'angular';
import ProjectsOrderScenesController from './order.controller.js';

const ProjectsOrderScenesModule = angular.module('pages.projects.edit.order', []);

ProjectsOrderScenesModule.controller(
    'ProjectsOrderScenesController', ProjectsOrderScenesController
);

export default ProjectsOrderScenesModule;

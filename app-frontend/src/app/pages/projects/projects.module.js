import ProjectsController from './projects.controller.js';

const ProjectsModule = angular.module('pages.projects', []);

ProjectsModule.controller('ProjectsController', ProjectsController);

export default ProjectsModule;

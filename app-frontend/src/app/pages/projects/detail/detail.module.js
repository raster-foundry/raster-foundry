import pagination from 'angular-ui-bootstrap/src/pagination';

import ProjectsDetailController from './detail.controller.js';

const ProjectsDetailModule = angular.module('pages.projects.detail', [pagination]);

ProjectsDetailModule.controller('ProjectsDetailController', ProjectsDetailController);

export default ProjectsDetailModule;

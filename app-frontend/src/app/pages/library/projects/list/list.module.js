import pagination from 'angular-ui-bootstrap/src/pagination';
import modal from 'angular-ui-bootstrap/src/modal';
require('./list.scss');

import ProjectsListController from './list.controller.js';

const ProjectsListModule = angular.module('pages.library.projects.list', [pagination, modal]);

ProjectsListModule.controller('ProjectsListController', ProjectsListController);

export default ProjectsListModule;

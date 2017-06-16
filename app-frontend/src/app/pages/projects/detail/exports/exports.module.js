import pagination from 'angular-ui-bootstrap/src/pagination';
import ProjectDetailExportsController from './exports.controller.js';

const ProjectDetailExportsModule = angular.module('pages.projects.detail.exports', [pagination]);
ProjectDetailExportsModule.controller(
    'ProjectDetailExportsController',
    ProjectDetailExportsController
);

export default ProjectDetailExportsModule;

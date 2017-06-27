import pagination from 'angular-ui-bootstrap/src/pagination';
import ProjectDetailScenesController from './scenes.controller.js';

const ProjectDetailScenesModule = angular.module('pages.projects.detail.scenes', [pagination]);
ProjectDetailScenesModule.controller(
    'ProjectDetailScenesController',
    ProjectDetailScenesController
);

export default ProjectDetailScenesModule;

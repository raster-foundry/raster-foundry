import pagination from 'angular-ui-bootstrap/src/pagination';

import ProjectScenesController from './projectScenes.controller.js';

const ProjectScenesModule = angular.module('pages.library.projects.detail.scenes',
                                           [pagination]);

ProjectScenesModule.controller('ProjectScenesController', ProjectScenesController);

export default ProjectScenesModule;

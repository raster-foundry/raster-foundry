import angular from 'angular';
import dropdown from 'angular-ui-bootstrap/src/dropdown';
import modal from 'angular-ui-bootstrap/src/modal';

import ProjectEditController from './projectEdit.controller.js';
import ProjectEditComponent from './projectEdit.component.js';

const ProjectEditModule = angular.module('pages.editor.project', [
    'components.mapContainer', modal, dropdown
]);

ProjectEditModule.component('rfProjectEditor', ProjectEditComponent);
ProjectEditModule.controller('ProjectEditController', ProjectEditController);

export default ProjectEditModule;

import angular from 'angular';
import dropdown from 'angular-ui-bootstrap/src/dropdown';
import modal from 'angular-ui-bootstrap/src/modal';
import ProjectEditController from './projectEdit.controller.js';

const ProjectEditModule = angular.module('pages.editor.project', [
    'components.leafletMap', modal, dropdown
]);

ProjectEditModule.controller('ProjectEditController', ProjectEditController);

export default ProjectEditModule;

import angular from 'angular';
import CreateProjectModalComponent from './createProjectModal.component.js';
import CreateProjectModalController from './createProjectModal.controller.js';

const CreateProjectModalModule = angular.module('components.createProjectModal', []);

CreateProjectModalModule.controller('CreateProjectModalController', CreateProjectModalController);
CreateProjectModalModule.component('rfCreateProjectModal', CreateProjectModalComponent);

export default CreateProjectModalModule;

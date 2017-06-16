import angular from 'angular';
import SceneImportModalComponent from './sceneImportModal.component.js';
import SceneImportModalController from './sceneImportModal.controller.js';

const SceneImportModalModule = angular.module(
    'components.scenes.sceneImportModal',
    ['ngFileUpload']
);

SceneImportModalModule.controller('SceneImportModalController', SceneImportModalController);
SceneImportModalModule.component('rfSceneImportModal', SceneImportModalComponent);

export default SceneImportModalModule;

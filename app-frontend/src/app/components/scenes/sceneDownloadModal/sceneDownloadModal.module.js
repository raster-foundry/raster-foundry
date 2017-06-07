import angular from 'angular';
import SceneDownloadModalComponent from './sceneDownloadModal.component.js';

const SceneDownloadModalModule = angular.module('components.scenes.sceneDownloadModal', []);

SceneDownloadModalModule.component(
    'rfSceneDownloadModal', SceneDownloadModalComponent
);

export default SceneDownloadModalModule;

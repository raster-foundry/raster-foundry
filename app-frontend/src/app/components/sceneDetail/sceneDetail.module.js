import angular from 'angular';
import SceneDetailComponent from './sceneDetail.component.js';
require('../../../assets/font/fontello/css/fontello.css');

const SceneDetailModule = angular.module('components.sceneDetail', []);

SceneDetailModule.component('rfSceneDetail', SceneDetailComponent);

export default SceneDetailModule;

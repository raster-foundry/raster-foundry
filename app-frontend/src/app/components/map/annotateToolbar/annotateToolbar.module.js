import angular from 'angular';
import AnnotateToolbarComponent from './annotateToolbar.component.js';
import AnnotateToolbarController from './annotateToolbar.controller.js';
require('./annotateToolbar.scss');

const AnnotateToolbarModule = angular.module('components.map.annotateToolbar', []);

AnnotateToolbarModule.component('rfAnnotateToolbar', AnnotateToolbarComponent);
AnnotateToolbarModule.controller('AnnotateToolbarController', AnnotateToolbarController);

export default AnnotateToolbarModule;

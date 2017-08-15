import angular from 'angular';
import AnnotateController from './annotate.controller.js';
require('./annotate.scss');

const AnnotateModule = angular.module('pages.projects.edit.annotate', ['cfp.hotkeys']);

AnnotateModule.controller(
    'AnnotateController', AnnotateController
);

export default AnnotateModule;

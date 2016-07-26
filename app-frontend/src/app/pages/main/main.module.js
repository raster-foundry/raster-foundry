'use strict';
import angular from 'angular';

import MainController from './main.controller.js';

const mainPageModule = angular.module('pages.main', [
    'ui.router'
]);

mainPageModule.controller(MainController);

export default mainPageModule;

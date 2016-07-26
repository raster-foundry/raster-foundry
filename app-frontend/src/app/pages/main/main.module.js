'use strict';
import angular from 'angular';

import route from './main.route';

const mainPageModule = angular.module('pages.main', [
    'ui.router'
]);

mainPageModule
    .config(route);

export default mainPageModule;

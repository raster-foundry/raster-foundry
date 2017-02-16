import angular from 'angular';

import ErrorController from './error.controller.js';

const ErrorModule = angular.module('pages.error', []);

ErrorModule.controller('ErrorController', ErrorController);

export default ErrorModule;

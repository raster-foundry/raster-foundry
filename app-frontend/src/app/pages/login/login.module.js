import angular from 'angular';
import LoginController from './login.controller.js';
require('./login.scss');

const LoginModule = angular.module('pages.login', []);

LoginModule.controller('LoginController', LoginController);

export default LoginModule;

import angular from 'angular';
import LoginController from './login.controller.js';

const LoginModule = angular.module('pages.login', []);

LoginModule.controller('LoginController', LoginController);

export default LoginModule;

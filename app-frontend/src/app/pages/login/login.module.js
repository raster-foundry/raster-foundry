import './login.scss';

import LoginController from './login.controller.js';

const LoginPageModule = angular.module('pages.login', []);

LoginPageModule.controller('LoginController', LoginController);

export default LoginPageModule;

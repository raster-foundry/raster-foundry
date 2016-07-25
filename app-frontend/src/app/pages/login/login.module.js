'use strict';

import './login.scss';

import loginController from './login.controller.js';
import loginComponent from './login.component.js';

const loginModule = angular.module('login-module', []);

loginModule.controller('loginController', loginController);

export default loginModule;

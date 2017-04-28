import HomeController from './home.controller.js';
require('./home.scss');

const HomeModule = angular.module('pages.home', []);

HomeModule.controller('HomeController', HomeController);

export default HomeModule;

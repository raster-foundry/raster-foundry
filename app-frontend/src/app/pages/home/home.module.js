import HomeController from './home.controller.js';

const HomeModule = angular.module('pages.home', []);

HomeModule.controller('HomeController', HomeController);

export default HomeModule;

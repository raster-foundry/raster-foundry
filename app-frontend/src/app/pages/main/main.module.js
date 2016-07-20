import MainController from './main.controller.js';

const MainPageModule = angular.module('pages.main', [
    'ui.router'
]);

MainPageModule.controller('MainController', MainController);

export default MainPageModule;

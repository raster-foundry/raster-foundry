import angular from 'angular';

import loginTpl from './pages/login/login.html';
import mainTpl from './pages/main/main.html';
function routeConfig($urlRouterProvider, $stateProvider) {
    'ngInject';

    $stateProvider
        .state('main', {
            url: '/',
            templateUrl: mainTpl,
            controller: 'MainController',
            controllerAs: '$ctrl'
        })
        .state('login', {
            url: '/login',
            templateUrl: loginTpl,
            controller: 'LoginController',
            controllerAs: '$ctrl'
        });
    $urlRouterProvider.otherwise('/');
}

export default angular
    .module('index.routes', [])
    .config(routeConfig);


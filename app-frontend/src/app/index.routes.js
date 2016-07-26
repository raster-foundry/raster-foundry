import angular from 'angular';

import loginTpl from './pages/login/login.html';
function routeConfig($urlRouterProvider, $stateProvider) {
    'ngInject';

    $stateProvider
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


import mainTpl from './pages/main/main.html';
import loginTpl from './pages/login/login.html';
import libraryTpl from './pages/library/library.html';
import projectTpl from './pages/library/project/project.html';
import accountTpl from './pages/account/account.html';
import billingTpl from './pages/account/billing/billing.html';
import keysTpl from './pages/account/keys/keys.html';

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
        })
        .state('library', {
            url: '/library',
            templateUrl: libraryTpl,
            controller: 'LibraryController',
            controllerAs: '$ctrl'
        })
        .state('project', {
            url: '/library/project',
            templateUrl: projectTpl,
            controller: 'ProjectController',
            controllerAs: '$ctrl'
        })
        .state('account', {
            url: '/account',
            templateUrl: accountTpl,
            controller: 'AccountController',
            controllerAs: '$ctrl'
        })
        .state('billing', {
            url: '/account/billing',
            templateUrl: billingTpl,
            controller: 'BillingController',
            controllerAs: '$ctrl'
        })
        .state('keys', {
            url: '/account/keys',
            templateUrl: keysTpl,
            controller: 'KeysController',
            controllerAs: '$ctrl'
        });
    $urlRouterProvider.otherwise('/');
}

export default angular
    .module('index.routes', [])
    .config(routeConfig);


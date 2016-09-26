import browseTpl from './pages/browse/browse.html';
import libraryTpl from './pages/library/library.html';
import scenesTpl from './pages/library/scenes/scenes.html';
import bucketsTpl from './pages/library/buckets/buckets.html';
import settingsTpl from './pages/settings/settings.html';
import profileTpl from './pages/settings/profile/profile.html';
import accountTpl from './pages/settings/account/account.html';


function routeConfig($urlRouterProvider, $stateProvider) {
    'ngInject';

    $stateProvider
        .state('browse', {
            url: '/',
            templateUrl: browseTpl,
            controller: 'BrowseController',
            controllerAs: '$ctrl'
        })
        .state('library', {
            url: '/library',
            templateUrl: libraryTpl,
            controller: 'LibraryController',
            controllerAs: '$ctrl',
            abstract: true
        })
        .state('library.scenes', {
            url: '/scenes',
            templateUrl: scenesTpl,
            controller: 'ScenesController',
            controllerAs: '$ctrl'
        })
        .state('library.buckets', {
            url: '/buckets',
            templateUrl: bucketsTpl,
            controller: 'BucketsController',
            controllerAs: '$ctrl'
        })
        .state('settings', {
            url: '/settings',
            templateUrl: settingsTpl,
            controller: 'SettingsController',
            controllerAs: '$ctrl',
            abstract: true

        })
        .state('settings.profile', {
            url: '/profile',
            templateUrl: profileTpl,
            controller: 'ProfileController',
            controllerAs: '$ctrl'
        })
        .state('settings.account', {
            url: '/account',
            templateUrl: accountTpl,
            controller: 'AccountController',
            controllerAs: '$ctrl'
        });
    $urlRouterProvider.otherwise('/');
}

export default angular
    .module('index.routes', [])
    .config(routeConfig);


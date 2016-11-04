import browseTpl from './pages/browse/browse.html';
import marketTpl from './pages/market/market.html';
import marketSearchTpl from './pages/market/search/search.html';
import marketModelTpl from './pages/market/model/model.html';
import libraryTpl from './pages/library/library.html';
import scenesTpl from './pages/library/scenes/scenes.html';
import scenesListTpl from './pages/library/scenes/list/list.html';
import sceneDetailTpl from './pages/library/scenes/detail/detail.html';
import bucketsTpl from './pages/library/buckets/buckets.html';
import bucketsListTpl from './pages/library/buckets/list/list.html';
import bucketsDetailTpl from './pages/library/buckets/detail/detail.html';
import bucketSceneTpl from './pages/library/buckets/detail/scene/scene.html';
import bucketScenesTpl from './pages/library/buckets/detail/bucketScenes/bucketScenes.html';
import settingsTpl from './pages/settings/settings.html';
import profileTpl from './pages/settings/profile/profile.html';
import accountTpl from './pages/settings/account/account.html';
import tokensTpl from './pages/settings/tokens/tokens.html';
import errorTpl from './pages/error/error.html';

function librarySceneStates($stateProvider) {
    $stateProvider
        .state('library.scenes', {
            url: '/scenes',
            templateUrl: scenesTpl,
            controller: 'ScenesController',
            controllerAs: '$ctrl',
            abstract: true
        })
        .state('library.scenes.detail', {
            url: '/detail/:id',
            templateUrl: sceneDetailTpl,
            params: {scene: null},
            controller: 'SceneDetailController',
            controllerAs: '$ctrl'
        })
        .state('library.scenes.list', {
            url: '/list?:page',
            templateUrl: scenesListTpl,
            controller: 'ScenesListController',
            controllerAs: '$ctrl'
        });
}

function libraryBucketStates($stateProvider) {
    $stateProvider
        .state('library', {
            url: '/library',
            templateUrl: libraryTpl,
            controller: 'LibraryController',
            controllerAs: '$ctrl',
            abstract: true
        })
        .state('library.buckets', {
            url: '/buckets',
            templateUrl: bucketsTpl,
            controller: 'BucketsController',
            controllerAs: '$ctrl',
            abstract: true
        })
        .state('library.buckets.list', {
            url: '/list?:page',
            templateUrl: bucketsListTpl,
            controller: 'BucketsListController',
            controllerAs: '$ctrl'
        })
        .state('library.buckets.detail', {
            url: '/detail/:bucketid',
            params: {bucket: null},
            templateUrl: bucketsDetailTpl,
            controller: 'BucketsDetailController',
            controllerAs: '$ctrl',
            abstract: true
        })
        .state('library.buckets.detail.scenes', {
            url: '/list?:page',
            templateUrl: bucketScenesTpl,
            params: {bucket: null},
            controller: 'BucketScenesController',
            controllerAs: '$ctrl'
        })
        .state('library.buckets.detail.scene', {
            url: '/scenes/:sceneid',
            templateUrl: bucketSceneTpl,
            params: {scene: null},
            controller: 'BucketSceneController',
            controllerAs: '$ctrl'
        });
}

function settingsStates($stateProvider) {
    $stateProvider
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
        })
        .state('settings.tokens', {
            url: '/tokens',
            templateUrl: tokensTpl,
            controller: 'TokensController',
            controllerAs: '$ctrl'
        });
}

function browseStates($stateProvider) {
    let queryParams = [
        'maxCloudCover',
        'minCloudCover',
        'minAcquisitionDatetime',
        'maxAcquisitionDatetime',
        'datasource',
        'month',
        'maxSunAzimuth',
        'minSunAzimuth',
        'maxSunElevation',
        'minSunElevation',
        'bbox',
        'point'
    ].join('&');

    $stateProvider
        .state('browse', {
            url: '/browse/:id?' + queryParams,
            templateUrl: browseTpl,
            controller: 'BrowseController',
            controllerAs: '$ctrl'
        });
}

function marketStates($stateProvider) {
    $stateProvider
        .state('market', {
            url: '/market',
            templateUrl: marketTpl,
            controller: 'MarketController',
            controllerAs: '$ctrl',
            abstract: true
        })
        .state('market.search', {
            url: '/search?:query',
            templateUrl: marketSearchTpl,
            controller: 'MarketSearchController',
            controllerAs: '$ctrl'
        })
        .state('market.model', {
            url: '/model/:id',
            params: {
                modelData: null
            },
            templateUrl: marketModelTpl,
            controller: 'MarketModelController',
            controllerAs: '$ctrl'
        });
}

function routeConfig($urlRouterProvider, $stateProvider) {
    'ngInject';

    browseStates($stateProvider);
    marketStates($stateProvider);
    librarySceneStates($stateProvider);
    libraryBucketStates($stateProvider);
    settingsStates($stateProvider);

    $stateProvider
        .state('error', {
            url: '/error',
            templateUrl: errorTpl,
            controller: 'ErrorController',
            controllerAs: '$ctrl'
        });

    $urlRouterProvider.otherwise('/browse/');
}


export default angular
    .module('index.routes', [])
    .config(routeConfig);


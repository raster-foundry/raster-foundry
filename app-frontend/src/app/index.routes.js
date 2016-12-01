import browseTpl from './pages/browse/browse.html';
import marketTpl from './pages/market/market.html';
import marketSearchTpl from './pages/market/search/search.html';
import marketModelTpl from './pages/market/model/model.html';
import editorTpl from './pages/editor/editor.html';
import libraryTpl from './pages/library/library.html';
import scenesTpl from './pages/library/scenes/scenes.html';
import scenesListTpl from './pages/library/scenes/list/list.html';
import sceneDetailTpl from './pages/library/scenes/detail/detail.html';
import projectsTpl from './pages/library/projects/projects.html';
import projectsListTpl from './pages/library/projects/list/list.html';
import projectsDetailTpl from './pages/library/projects/detail/detail.html';
import projectSceneTpl from './pages/library/projects/detail/scene/scene.html';
import projectScenesTpl from './pages/library/projects/detail/projectScenes/projectScenes.html';
import projectEditTpl from './pages/editor/project/projectEdit.html';
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

function editorStates($stateProvider) {
    $stateProvider
        .state('editor', {
            url: '/editor',
            templateUrl: editorTpl,
            controller: 'EditorController',
            controllerAs: '$ctrl',
            abstract: true
        })
        .state('editor.project', {
            url: '/project/:projectid',
            params: {project: null},
            templateUrl: projectEditTpl,
            controller: 'ProjectEditController',
            controllerAs: '$ctrl'
        });
}

function libraryProjectStates($stateProvider) {
    $stateProvider
        .state('library', {
            url: '/library',
            templateUrl: libraryTpl,
            controller: 'LibraryController',
            controllerAs: '$ctrl',
            abstract: true
        })
        .state('library.projects', {
            url: '/projects',
            templateUrl: projectsTpl,
            controller: 'ProjectsController',
            controllerAs: '$ctrl',
            abstract: true
        })
        .state('library.projects.list', {
            url: '/list?:page',
            templateUrl: projectsListTpl,
            controller: 'ProjectsListController',
            controllerAs: '$ctrl'
        })
        .state('library.projects.detail', {
            url: '/detail/:projectid',
            params: {project: null},
            templateUrl: projectsDetailTpl,
            controller: 'ProjectsDetailController',
            controllerAs: '$ctrl',
            abstract: true
        })
        .state('library.projects.detail.scenes', {
            url: '/list?:page',
            templateUrl: projectScenesTpl,
            params: {project: null},
            controller: 'ProjectScenesController',
            controllerAs: '$ctrl'
        })
        .state('library.projects.detail.scene', {
            url: '/scenes/:sceneid',
            templateUrl: projectSceneTpl,
            params: {scene: null},
            controller: 'ProjectSceneController',
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
    editorStates($stateProvider);
    librarySceneStates($stateProvider);
    libraryProjectStates($stateProvider);
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

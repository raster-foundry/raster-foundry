/* eslint max-len: 0 */
import rootTpl from './pages/root/root.html';
import loginTpl from './pages/login/login.html';
import browseTpl from './pages/browse/browse.html';
import labTpl from './pages/lab/lab.html';
import labEditTpl from './pages/lab/edit/edit.html';
import labRunTpl from './pages/lab/run/run.html';
import marketTpl from './pages/market/market.html';
import marketSearchTpl from './pages/market/search/search.html';
import marketToolTpl from './pages/market/tool/tool.html';
import editorTpl from './pages/editor/editor.html';
import colorCorrectScenesStateTpl from
    './components/colorCorrectScenes/colorCorrectScenes.state.html';
import colorCorrectPaneStateTpl from './components/colorCorrectPane/colorCorrectPane.state.html';
import mosaicScenesStateTpl from './components/mosaicScenes/mosaicScenes.state.html';
import mosaicMaskStateTpl from './components/mosaicMask/mosaicMask.state.html';
import libraryTpl from './pages/library/library.html';
import scenesTpl from './pages/library/scenes/scenes.html';
import scenesListTpl from './pages/library/scenes/list/list.html';
import sceneDetailTpl from './pages/library/scenes/detail/detail.html';
import projectsTpl from './pages/library/projects/projects.html';
import projectsListTpl from './pages/library/projects/list/list.html';
import projectsDetailTpl from './pages/library/projects/detail/detail.html';
import projectSceneTpl from './pages/library/projects/detail/scene/scene.html';
import projectScenesTpl from './pages/library/projects/detail/projectScenes/projectScenes.html';
import settingsTpl from './pages/settings/settings.html';
import profileTpl from './pages/settings/profile/profile.html';
import accountTpl from './pages/settings/account/account.html';
import tokensTpl from './pages/settings/tokens/tokens.html';
import apiTokensTpl from './pages/settings/tokens/api/api.html';
import mapTokensTpl from './pages/settings/tokens/map/map.html';
import errorTpl from './pages/error/error.html';
import shareTpl from './pages/share/share.html';
import homeTpl from './pages/home/home.html';
import importsTpl from './pages/imports/imports.html';
import importsDatasourcesTpl from './pages/imports/datasources/datasources.html';
import importsDatasourcesListTpl from './pages/imports/datasources/list/list.html';
import importsDatasourcesDetailTpl from './pages/imports/datasources/detail/detail.html';
import datasourceColorCompositesTpl from './pages/imports/datasources/detail/colorComposites/colorComposites.html';
import datasourceColorCorrectionTpl from './pages/imports/datasources/detail/colorCorrection/colorCorrection.html';


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

function libraryProjectStates($stateProvider) {
    $stateProvider
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

function libraryStates($stateProvider) {
    $stateProvider
        .state('library', {
            parent: 'root',
            url: '/library',
            templateUrl: libraryTpl,
            controller: 'LibraryController',
            controllerAs: '$ctrl',
            abstract: true
        });
    librarySceneStates($stateProvider);
    libraryProjectStates($stateProvider);
}

function editorStates($stateProvider) {
    $stateProvider
        .state('editor', {
            parent: 'root',
            url: '/editor',
            templateUrl: editorTpl,
            controller: 'EditorController',
            controllerAs: '$ctrl',
            abstract: true
        })
        .state('editor.project', {
            url: '/project/:projectid?',
            template: '<rf-project-editor class="app-content"></rf-project-editor>',
            abstract: true
        })
        .state('editor.project.color', {
            url: '/color-correct',
            template: '<ui-view class="flex-column"></ui-view>',
            abstract: true
        })
        .state('editor.project.color.scenes', {
            url: '/scenes',
            templateUrl: colorCorrectScenesStateTpl
        })
        .state('editor.project.color.adjust', {
            url: '/adjust',
            params: {
                layers: null
            },
            templateUrl: colorCorrectPaneStateTpl
        })
        .state('editor.project.mosaic', {
            url: '/mosaic',
            template: '<ui-view class="flex-column"></ui-view>',
            abstract: true
        })
        .state('editor.project.mosaic.scenes', {
            url: '/scenes',
            templateUrl: mosaicScenesStateTpl
        })
        .state('editor.project.mosaic.params', {
            url: '/params',
            template: '<rf-mosaic-params class="flex-column sidebar-dark"></rf-mosaic-params>'
        })
        .state('editor.project.mosaic.mask', {
            url: '/mask/:sceneid',
            params: {
                scene: null
            },
            templateUrl: mosaicMaskStateTpl
        });
}

function settingsStates($stateProvider) {
    $stateProvider
        .state('settings', {
            parent: 'root',
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
            controllerAs: '$ctrl',
            abstract: true
        })
        .state('settings.tokens.api', {
            url: '/api',
            templateUrl: apiTokensTpl,
            controller: 'ApiTokensController',
            controllerAs: '$ctrl'
        })
        .state('settings.tokens.map', {
            url: '/map',
            templateUrl: mapTokensTpl,
            controller: 'MapTokensController',
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
        'maxSunAzimuth',
        'minSunAzimuth',
        'maxSunElevation',
        'minSunElevation',
        'bbox',
        'point',
        'ingested'
    ].join('&');

    $stateProvider
        .state('browse', {
            parent: 'root',
            url: '/browse/:projectid/:sceneid?' + queryParams,
            templateUrl: browseTpl,
            controller: 'BrowseController',
            controllerAs: '$ctrl'
        });
}

function marketStates($stateProvider) {
    $stateProvider
        .state('market', {
            parent: 'root',
            url: '/market',
            templateUrl: marketTpl,
            controller: 'MarketController',
            controllerAs: '$ctrl',
            abstract: true
        })
        .state('market.search', {
            url: '/search?:query?toolcategory&tooltag',
            templateUrl: marketSearchTpl,
            controller: 'MarketSearchController',
            controllerAs: '$ctrl'
        })
        .state('market.tool', {
            url: '/tool/:id',
            params: {
                modelData: null
            },
            templateUrl: marketToolTpl,
            controller: 'MarketToolController',
            controllerAs: '$ctrl'
        });
}

function labStates($stateProvider) {
    $stateProvider
        .state('lab', {
            parent: 'root',
            url: '/lab/:toolid',
            templateUrl: labTpl,
            controller: 'LabController',
            controllerAs: '$ctrl',
            abstract: true
        })
        .state('lab.edit', {
            url: '/edit',
            templateUrl: labEditTpl,
            controller: 'LabEditController',
            controllerAs: '$ctrl'
        })
        .state('lab.run', {
            url: '/run/:projectid?',
            templateUrl: labRunTpl,
            controller: 'LabRunController',
            controllerAs: '$ctrl'
        });
}

function shareStates($stateProvider) {
    $stateProvider
        .state('share', {
            url: '/share/:projectid',
            templateUrl: shareTpl,
            controller: 'ShareController',
            controllerAs: '$ctrl'
        });
}

function loginStates($stateProvider) {
    $stateProvider
        .state('login', {
            url: '/login',
            templateUrl: loginTpl,
            controller: 'LoginController',
            controllerAs: '$ctrl'
        });
}

function homeStates($stateProvider) {
    $stateProvider
        .state('home', {
            parent: 'root',
            url: '/home',
            templateUrl: homeTpl,
            controller: 'HomeController',
            controllerAs: '$ctrl'
        });
}

function importStates($stateProvider) {
    $stateProvider
        .state('imports', {
            parent: 'root',
            url: '/imports',
            templateUrl: importsTpl,
            controller: 'ImportsController',
            controllerAs: '$ctrl'
        })
        .state('imports.datasources', {
            url: '/datasources',
            templateUrl: importsDatasourcesTpl,
            controller: 'DatasourcesController',
            controllerAs: '$ctrl',
            abstract: true
        })
        .state('imports.datasources.list', {
            url: '/list?:page',
            templateUrl: importsDatasourcesListTpl,
            controller: 'DatasourceListController',
            controllerAs: '$ctrl'
        })
        .state('imports.datasources.detail', {
            url: '/detail/:datasourceid',
            templateUrl: importsDatasourcesDetailTpl,
            controller: 'DatasourceDetailController',
            controllerAs: '$ctrl'
        })
        .state('imports.datasources.detail.colorComposites', {
            url: '/color-composites',
            templateUrl: datasourceColorCompositesTpl,
            controller: 'ColorCompositesController',
            controllerAs: '$ctrl'
        })
        .state('imports.datasources.detail.colorCorrection', {
            url: '/color-correction',
            templateUrl: datasourceColorCorrectionTpl,
            controller: 'ColorCorrectionController',
            controllerAs: '$ctrl'
        })
        .state('imports.scenes', {
            url: '/scenes',
            template: '<div>Scenes</div>'
        });
}

function routeConfig($urlRouterProvider, $stateProvider, $urlMatcherFactoryProvider) {
    'ngInject';

    $urlMatcherFactoryProvider.strictMode(false);

    $stateProvider.state('root', {
        templateUrl: rootTpl
    });

    loginStates($stateProvider);
    browseStates($stateProvider);
    marketStates($stateProvider);
    editorStates($stateProvider);
    libraryStates($stateProvider);
    settingsStates($stateProvider);
    labStates($stateProvider);
    shareStates($stateProvider);
    homeStates($stateProvider);
    importStates($stateProvider);

    $stateProvider
        .state('error', {
            url: '/error',
            templateUrl: errorTpl,
            controller: 'ErrorController',
            controllerAs: '$ctrl'
        });

    $urlRouterProvider.otherwise('/home');
}


export default angular
    .module('index.routes', [])
    .config(routeConfig);

/* eslint max-len: 0 */
import rootTpl from './pages/root/root.html';
import loginTpl from './pages/login/login.html';
import labTpl from './pages/lab/lab.html';
import labEditTpl from './pages/lab/edit/edit.html';
import labRunTpl from './pages/lab/run/run.html';
import labNavbarTpl from './pages/lab/run/navbar/navbar.html';
import marketTpl from './pages/market/market.html';
import marketSearchTpl from './pages/market/search/search.html';
import marketToolTpl from './pages/market/tool/tool.html';

import projectsTpl from './pages/projects/projects.html';
import projectsNavbarTpl from './pages/projects/navbar/navbar.html';
import projectsEditTpl from './pages/projects/edit/edit.html';
import projectsEditColorTpl from './pages/projects/edit/color/color.html';
import projectsEditColormodeTpl from './pages/projects/edit/colormode/colormode.html';
import projectsAdvancedColorTpl from './pages/projects/edit/advancedcolor/advancedcolor.html';
import projectsColorAdjustTpl from './pages/projects/edit/advancedcolor/adjust/adjust.html';
import projectsListTpl from './pages/projects/list/list.html';
import projectsDetailTpl from './pages/projects/detail/detail.html';
import projectsDetailScenesTpl from './pages/projects/detail/scenes/scenes.html';
import projectsDetailExportsTpl from './pages/projects/detail/exports/exports.html';
import projectsScenesTpl from './pages/projects/edit/scenes/scenes.html';
import projectsSceneBrowserTpl from './pages/projects/edit/browse/browse.html';
import projectOrderScenesTpl from './pages/projects/edit/order/order.html';
import projectMaskingTpl from './pages/projects/edit/masking/masking.html';
import projectMaskingDrawTpl from './pages/projects/edit/masking/draw/draw.html';
import aoiApproveTpl from './pages/projects/edit/aoi-approve/aoi-approve.html';
import aoiParametersTpl from './pages/projects/edit/aoi-parameters/aoi-parameters.html';
import exportTpl from './pages/projects/edit/exports/exports.html';
import newExportTpl from './pages/projects/edit/exports/new/new.html';
import annotateTpl from './pages/projects/edit/annotate/annotate.html';
import annotateImportTpl from './pages/projects/edit/annotate/import/import.html';
import annotateExportTpl from './pages/projects/edit/annotate/export/export.html';
import projectSharingTpl from './pages/projects/edit/sharing/sharing.html';

import settingsTpl from './pages/settings/settings.html';
import profileTpl from './pages/settings/profile/profile.html';
import tokensTpl from './pages/settings/tokens/tokens.html';
import apiTokensTpl from './pages/settings/tokens/api/api.html';
import mapTokensTpl from './pages/settings/tokens/map/map.html';
import connectionsTpl from './pages/settings/connections/connections.html';
import errorTpl from './pages/error/error.html';
import shareTpl from './pages/share/share.html';
import homeTpl from './pages/home/home.html';
import importsTpl from './pages/imports/imports.html';
import importsDatasourcesTpl from './pages/imports/datasources/datasources.html';
import importsDatasourcesListTpl from './pages/imports/datasources/list/list.html';
import importsDatasourcesDetailTpl from './pages/imports/datasources/detail/detail.html';
import datasourceColorCompositesTpl from './pages/imports/datasources/detail/colorComposites/colorComposites.html';

function projectEditStates($stateProvider) {
    let addScenesQueryParams = [
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
        'ingested',
        'owner'
    ].join('&');

    $stateProvider
        .state('projects.edit', {
            title: 'Project: Edit',
            url: '/edit/:projectid',
            params: {project: null},
            views: {
                'navmenu@root': {
                    templateUrl: projectsNavbarTpl,
                    controller: 'ProjectsNavbarController',
                    controllerAs: '$ctrl'
                },
                '': {
                    templateUrl: projectsEditTpl,
                    controller: 'ProjectsEditController',
                    controllerAs: '$ctrl'
                }
            },
            redirectTo: 'projects.edit.scenes'
        })
        .state('projects.edit.colormode', {
            title: 'Project: Color Mode',
            url: '/colormode',
            templateUrl: projectsEditColormodeTpl,
            controller: 'ProjectsEditColormodeController',
            controllerAs: '$ctrl'
        })
        .state('projects.edit.color', {
            title: 'Project: Color Correct',
            url: '/color',
            templateUrl: projectsEditColorTpl,
            controller: 'ProjectsEditColorController',
            controllerAs: '$ctrl'
        })
        .state('projects.edit.advancedcolor', {
            title: 'Project: Color Correct',
            url: '/advancedcolor',
            templateUrl: projectsAdvancedColorTpl,
            controller: 'ProjectsAdvancedColorController',
            controllerAs: '$ctrl'
        })
        .state('projects.edit.advancedcolor.adjust', {
            title: 'Project: Color Correct',
            url: '/adjust',
            templateUrl: projectsColorAdjustTpl,
            controller: 'ProjectsColorAdjustController',
            controllerAs: '$ctrl'
        })
        .state('projects.edit.scenes', {
            title: 'Project: Scenes',
            url: '/scenes',
            templateUrl: projectsScenesTpl,
            controller: 'ProjectsScenesController',
            controllerAs: '$ctrl'
        })
        .state('projects.edit.browse', {
            title: 'Project Scenes',
            url: '/browse/:sceneid?' + addScenesQueryParams,
            templateUrl: projectsSceneBrowserTpl,
            controller: 'ProjectsSceneBrowserController',
            controllerAs: '$ctrl'
        })
        .state('projects.edit.order', {
            url: '/order',
            templateUrl: projectOrderScenesTpl,
            controller: 'ProjectsOrderScenesController',
            controllerAs: '$ctrl'
        })
        .state('projects.edit.masking', {
            url: '/masking',
            templateUrl: projectMaskingTpl,
            controller: 'ProjectsMaskingController',
            controllerAs: '$ctrl'
        })
        .state('projects.edit.masking.draw', {
            url: '/mask',
            templateUrl: projectMaskingDrawTpl,
            controller: 'ProjectsMaskingDrawController',
            controllerAs: '$ctrl'
        })
        .state('projects.edit.aoi-approve', {
            title: 'Project: Pending Scenes',
            url: '/aoi-approve',
            templateUrl: aoiApproveTpl,
            controller: 'AOIApproveController',
            controllerAs: '$ctrl'
        })
        .state('projects.edit.aoi-parameters', {
            title: 'Project: AOI',
            url: '/aoi-parameters',
            templateUrl: aoiParametersTpl,
            controller: 'AOIParametersController',
            controllerAs: '$ctrl'
        })
        .state('projects.edit.exports', {
            title: 'Project: Exports',
            url: '/exports',
            templateUrl: exportTpl,
            controller: 'ExportController',
            controllerAs: '$ctrl'
        })
        .state('projects.edit.exports.new', {
            title: 'Project: New export',
            url: '/new',
            templateUrl: newExportTpl,
            controller: 'NewExportController',
            controllerAs: '$ctrl'
        })
        .state('projects.edit.annotate', {
            title: 'Project: Annotate',
            url: '/annotate',
            templateUrl: annotateTpl,
            controller: 'AnnotateController',
            controllerAs: '$ctrl'
        })
        .state('projects.edit.annotate.import', {
            url: '/import',
            templateUrl: annotateImportTpl,
            controller: 'AnnotateImportController',
            controllerAs: '$ctrl'
        })
        .state('projects.edit.annotate.export', {
            url: '/export',
            templateUrl: annotateExportTpl,
            controller: 'AnnotateExportController',
            controllerAs: '$ctrl'
        })
        .state('projects.edit.sharing', {
            url: '/sharing',
            templateUrl: projectSharingTpl,
            controller: 'SharingController',
            controllerAs: '$ctrl'
        });
}

function projectStates($stateProvider) {
    $stateProvider
        .state('projects', {
            parent: 'root',
            url: '/projects',
            templateUrl: projectsTpl,
            controller: 'ProjectsController',
            controllerAs: '$ctrl',
            abstract: true
        })
        .state('projects.list', {
            title: 'User Projects',
            url: '/list?:page',
            templateUrl: projectsListTpl,
            controller: 'ProjectsListController',
            controllerAs: '$ctrl'
        })
        .state('projects.detail', {
            url: '/detail/:projectid',
            params: {project: null},
            templateUrl: projectsDetailTpl,
            controller: 'ProjectsDetailController',
            controllerAs: '$ctrl',
            redirectTo: 'projects.detail.scenes'
        })
        .state('projects.detail.scenes', {
            title: 'Project: Details',
            url: '/scenes?:page',
            templateUrl: projectsDetailScenesTpl,
            controller: 'ProjectDetailScenesController',
            controllerAs: '$ctrl'
        })
        .state('projects.detail.exports', {
            title: 'Project: Exports',
            url: '/exports?:page',
            templateUrl: projectsDetailExportsTpl,
            controller: 'ProjectDetailExportsController',
            controllerAs: '$ctrl'
        });

    projectEditStates($stateProvider);
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
            title: 'Profile Settings',
            url: '/profile',
            templateUrl: profileTpl,
            controller: 'ProfileController',
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
            title: 'Settings: API Tokens',
            url: '/api',
            templateUrl: apiTokensTpl,
            controller: 'ApiTokensController',
            controllerAs: '$ctrl'
        })
        .state('settings.tokens.map', {
            title: 'Settings: Map Tokens',
            url: '/map',
            templateUrl: mapTokensTpl,
            controller: 'MapTokensController',
            controllerAs: '$ctrl'
        })
        .state('settings.connections', {
            title: 'Settings: API Connections',
            url: '/connections',
            templateUrl: connectionsTpl,
            controller: 'ConnectionsController',
            controllerAs: '$ctrl'
        });
}

function marketStates($stateProvider) {
    $stateProvider
        .state('market', {
            title: 'Tools',
            parent: 'root',
            url: '/market',
            templateUrl: marketTpl,
            controller: 'MarketController',
            controllerAs: '$ctrl',
            abstract: true
        })
        .state('market.search', {
            title: 'Tool Search',
            url: '/search?:page?:query?toolcategory&tooltag',
            templateUrl: marketSearchTpl,
            controller: 'MarketSearchController',
            controllerAs: '$ctrl'
        })
        .state('market.tool', {
            title: 'Tool Details',
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
            title: 'Tool Lab',
            parent: 'root',
            url: '/lab/:toolid',
            templateUrl: labTpl,
            controller: 'LabController',
            controllerAs: '$ctrl',
            abstract: true
        })
        .state('lab.edit', {
            title: 'Tool: Edit',
            url: '/edit',
            templateUrl: labEditTpl,
            controller: 'LabEditController',
            controllerAs: '$ctrl'
        })
       .state('lab.run', {
           title: 'Tool: Run',
           url: '/run/:runid?',
           views: {
               'navmenu@root': {
                   templateUrl: labNavbarTpl,
                   controller: 'LabNavbarController',
                   controllerAs: '$ctrl'
               },
               '': {
                   templateUrl: labRunTpl,
                   controller: 'LabRunController',
                   controllerAs: '$ctrl'
               }
           }
       });
}

function shareStates($stateProvider) {
    $stateProvider
        .state('share', {
            title: 'Shared Project',
            url: '/share/:projectid',
            templateUrl: shareTpl,
            controller: 'ShareController',
            controllerAs: '$ctrl'
        });
}

function loginStates($stateProvider) {
    $stateProvider
        .state('login', {
            title: 'Login',
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
            title: 'Imports',
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
            title: 'Datasources',
            url: '/list?:page',
            templateUrl: importsDatasourcesListTpl,
            controller: 'DatasourceListController',
            controllerAs: '$ctrl'
        })
        .state('imports.datasources.detail', {
            title: 'Datasource Details',
            url: '/detail/:datasourceid',
            templateUrl: importsDatasourcesDetailTpl,
            controller: 'DatasourceDetailController',
            controllerAs: '$ctrl'
        })
        .state('imports.datasources.detail.colorComposites', {
            title: 'Datasource Color Composites',
            url: '/color-composites',
            templateUrl: datasourceColorCompositesTpl,
            controller: 'ColorCompositesController',
            controllerAs: '$ctrl'
        });
}

function routeConfig($urlRouterProvider, $stateProvider, $urlMatcherFactoryProvider, $locationProvider) {
    'ngInject';

    $urlMatcherFactoryProvider.strictMode(false);
    $locationProvider.html5Mode(true);


    $stateProvider.state('root', {
        templateUrl: rootTpl
    }).state('callback', {
        url: '/callback'
    });

    loginStates($stateProvider);
    marketStates($stateProvider);
    projectStates($stateProvider);
    settingsStates($stateProvider);
    labStates($stateProvider);
    shareStates($stateProvider);
    homeStates($stateProvider);
    importStates($stateProvider);

    $stateProvider
        .state('error', {
            title: 'Server Error',
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

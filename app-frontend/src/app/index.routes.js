/* eslint max-len: 0 */
import _ from 'lodash';
import { Map } from 'immutable';

import rootTpl from './pages/root/root.html';
import loginTpl from './pages/login/login.html';

import labBrowseTpl from './pages/lab/browse/browse.html';
import labBrowseModule from './pages/lab/browse/browse';
import labBrowseAnalysesTpl from './pages/lab/browse/analyses/analyses.html';
import labBrowseTemplatesTpl from './pages/lab/browse/templates/templates.html';
import labTemplateTpl from './pages/lab/template/template.html';
import labAnalysisTpl from './pages/lab/analysis/analysis.html';
import labAnalysisModule from './pages/lab/analysis/analysis';
import labStartAnalysisTpl from './pages/lab/startAnalysis/startAnalysis.html';
import labNavbarTpl from './pages/lab/navbar/navbar.html';

import projectsModule from './pages/projects/projects.module';
import projectsTpl from './pages/projects/projects.html';
import projectsNavbarTpl from './pages/projects/navbar/navbar.html';
import projectsEditTpl from './pages/projects/edit/edit.html';

import projectsListTpl from './pages/projects/list/list.html';
import projectsScenesTpl from './pages/projects/edit/scenes/scenes.html';
import projectsSceneBrowserTpl from './pages/projects/edit/browse/browse.html';
import projectOrderScenesTpl from './pages/projects/edit/order/order.html';
import projectMaskingTpl from './pages/projects/edit/masking/masking.html';
import projectMaskingDrawTpl from './pages/projects/edit/masking/draw/draw.html';
import exportTpl from './pages/projects/edit/exports/exports.html';
import newExportTpl from './pages/projects/edit/exports/new/new.html';
import annotateTpl from './pages/projects/edit/annotate/annotate.html';
import annotateImportTpl from './pages/projects/edit/annotate/import/import.html';
import annotateExportTpl from './pages/projects/edit/annotate/export/export.html';
import projectSharingTpl from './pages/projects/edit/sharing/sharing.html';

import userTpl from './pages/user/user.html';
import userModule from './pages/user/user';
import userSettingsTpl from './pages/user/settings/settings.html';
import userSettingsProfileTpl from './pages/user/settings/profile/profile.html';
import userSettingsApiTokensTpl from './pages/user/settings/api/api.html';
import userSettingsMapTokensTpl from './pages/user/settings/map/map.html';
import userSettingsConnectionsTpl from './pages/user/settings/connections/connections.html';
import userSettingsPrivacyTpl from './pages/user/settings/privacy/privacy.html';
import userSettingsNotificationTpl from './pages/user/settings/notification/notification.html';
import userOrganizationsTpl from './pages/user/organizations/organizations.html';
import userTeamsTpl from './pages/user/teams/teams.html';
import userProjectsTpl from './pages/user/projects/projects.html';
import userRastersTpl from './pages/user/rasters/rasters.html';

import errorTpl from './pages/error/error.html';
import shareTpl from './pages/share/share.html';
import homeTpl from './pages/home/home.html';
import importsTpl from './pages/imports/imports.html';
import importsModule from './pages/imports/imports.module';
import rasterListTpl from './pages/imports/raster/raster.html';
import vectorListTpl from './pages/imports/vector/vector.html';
import importsDatasourcesTpl from './pages/imports/datasources/datasources.html';
import importsDatasourcesListTpl from './pages/imports/datasources/list/list.html';
import importsDatasourcesDetailTpl from './pages/imports/datasources/detail/detail.html';

import adminTpl from './pages/admin/admin.html';
import organizationTpl from './pages/admin/organization/organization.html';
import organizationModule from './pages/admin/organization/organization';
import organizationMetricsTpl from './pages/admin/organization/metrics/metrics.html';
import organizationUsersTpl from './pages/admin/organization/users/users.html';
import organizationTeamsTpl from './pages/admin/organization/teams/teams.html';
import organizationSettingsTpl from './pages/admin/organization/settings/settings.html';
import organizationProjectsTpl from './pages/admin/organization/projects/projects.html';
import organizationRastersTpl from './pages/admin/organization/rasters/rasters.html';
import organizationVectorsTpl from './pages/admin/organization/vectors/vectors.html';
import organizationDatasourcesTpl from './pages/admin/organization/datasources/datasources.html';
import organizationTemplatesTpl from './pages/admin/organization/templates/templates.html';
import organizationAnalysesTpl from './pages/admin/organization/analyses/analyses.html';
import platformTpl from './pages/admin/platform/platform.html';
import platformModule from './pages/admin/platform/platform.module';
import platformMetricsTpl from './pages/admin/platform/metrics/metrics.html';
import platformUsersTpl from './pages/admin/platform/users/users.html';
import platformOrganizationsTpl from './pages/admin/platform/organizations/organizations.html';
import platformSettingsTpl from './pages/admin/platform/settings/settings.html';
import platformSettingsEmailTpl from './pages/admin/platform/settings/email/email.html';
import platformProjectsTpl from './pages/admin/platform/projects/projects.html';
import platformRastersTpl from './pages/admin/platform/rasters/rasters.html';

import platformVectorsTpl from './pages/admin/platform/vectors/vectors.html';
import platformDatasourcesTpl from './pages/admin/platform/datasources/datasources.html';
import platformTemplatesTpl from './pages/admin/platform/templates/templates.html';
import platformAnalysesTpl from './pages/admin/platform/analyses/analyses.html';
import teamTpl from './pages/admin/team/team.html';
import teamModule from './pages/admin/team/team';
import teamUsersTpl from './pages/admin/team/users/users.html';
import teamProjectsTpl from './pages/admin/team/projects/projects.html';
import teamRastersTpl from './pages/admin/team/rasters/rasters.html';
import teamVectorsTpl from './pages/admin/team/vectors/vectors.html';
import teamDatasourcesTpl from './pages/admin/team/datasources/datasources.html';
import teamTemplatesTpl from './pages/admin/team/templates/templates.html';
import teamAnalysesTpl from './pages/admin/team/analyses/analyses.html';

import { projectResolves } from './components/pages/project';

function shareStatesV2($stateProvider) {
  $stateProvider
    .state('shareProject', {
      title: 'Share',
      redirectTo: 'shareProject.layers',
      component: 'rfShareProjectPage',
      url: '/v2/share/:projectId?mapToken',
      resolve: Object.assign({
        mapToken: ['$transition$', $transition$ => $transition$.params().mapToken],
        projectPromise: [
          '$transition$',
          'projectService',
          ($transition$, projectService) =>
            projectService.fetchProject($transition$.params().projectId, {
              mapToken: $transition$.params().mapToken
            })
        ]
      }),
      resolvePolicy: { async: 'NOWAIT' },
      bypassAuth: true
    })
    .state('shareProject.error', {
      title: 'Invalid Share',
      url: '/error',
      component: 'rfShareProjectErrorPage',
      bypassAuth: true
    })
    .state('shareProject.layers', {
      title: 'Share Layers',
      url: '/layers?page',
      params: {
        page: { dynamic: true }
      },
      component: 'rfShareProjectLayersPage',
      bypassAuth: true
    })
    .state('shareProject.analyses', {
      title: 'Share Analyses',
      url: '/analyses?page',
      params: {
        page: { dynamic: true }
      },
      component: 'rfShareProjectAnalysesPage',
      bypassAuth: true
    });
}

function projectStatesV2($stateProvider) {
  let addScenesQueryParamsList = [
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
  ];
  let dynamicSceneParams = addScenesQueryParamsList
    .reduce((acc, param) => acc.set(param, { dynamic: true }), new Map())
    .toJSON();
  let addScenesQueryParams = addScenesQueryParamsList.join('&');

  $stateProvider
    .state('project', {
      parent: 'root',
      title: 'Project',
      url: '/v2/project/:projectId',
      resolve: Object.assign(
        {
          projectId: ['$transition$', $transition$ => $transition$.params().projectId],
          project: [
            '$transition$',
            'projectService',
            ($transition$, projectService) =>
              projectService.fetchProject($transition$.params().projectId)
          ]
        },
        projectResolves.resolve
      ),
      redirectTo: 'project.layers',
      views: {
        projectlayernav: {
          component: 'rfProjectLayersNav'
        },
        '': {
          component: 'rfProjectPage'
        }
      }
    })
    .state('project.layers', {
      title: 'Project Layers',
      url: '/layers?page',
      params: {
        page: { dynamic: true }
      },
      component: 'rfProjectLayersPage'
    })
    // top level project routes
    .state('project.analyses', {
      title: 'Project Analyses',
      url: '/analyses?page&search',
      component: 'rfProjectAnalysesPage'
    })
    .state('project.settings', {
      title: 'Project Settings',
      url: '/settings',
      redirectTo: 'project.settings.options',
      views: {
        'navbar-secondary': {
          component: 'rfProjectSettingsNavbar'
        },
        'projectlayernav@root': {
          component: 'rfProjectLayersNav'
        },
        '': {
          component: 'rfProjectSettingsPage'
        }
      }
    })
    .state('project.layer', {
      title: 'Project Layer',
      url: '/layer/:layerId',
      resolve: {
        layerId: ['$transition$', $transition$ => $transition$.params().layerId],
        layer: [
          '$transition$',
          'projectService',
          ($transition$, projectService) =>
            projectService.getProjectLayer(
              $transition$.params().projectId,
              $transition$.params().layerId
            )
        ]
      },
      views: {
        'projectlayernav@root': {
          component: 'rfProjectLayersNav'
        },
        'navbar-secondary': {
          component: 'rfProjectLayerSecondaryNavbar'
        },
        '': {
          component: 'rfProjectLayerPage'
        }
      },
      redirectTo: 'project.layer.scenes'
    })
    .state('project.layer.colormode', {
      title: 'Project Layer Colormode',
      url: '/colormode',
      component: 'rfProjectLayerColormodePage'
    })
    .state('project.layer.corrections', {
      title: 'Project Layer Corrections',
      url: '/corrections',
      component: 'rfProjectLayerCorrectionsPage'
    })
    .state('project.layer.scenes', {
      title: 'Project Layer Scenes',
      url: '/scenes?page',
      component: 'rfProjectLayerScenesPage'
    })
    .state('project.layer.browse', {
      title: 'Find Scenes',
      url: '/browse?' + addScenesQueryParams,
      views: {
        'project-sidemodal@project': {
          component: 'rfProjectLayerScenesBrowsePage'
        }
      },
      params: dynamicSceneParams
    })
    .state('project.layer.exports', {
      title: 'Project Layer Exports',
      url: '/exports?page',
      component: 'rfProjectLayerExportsPage'
    })
    .state('project.layer.export', {
      title: 'Project Layer Export',
      url: '/export',
      component: 'rfProjectLayerExportPage'
    })
    .state('project.layer.annotations', {
      title: 'Project Layer Annotations',
      url: '/annotations?page',
      component: 'rfProjectLayerAnnotationsPage'
    })
    // Project analyses routes
    .state('project.analyses.quickedit', {
      title: 'Edit histograms on map',
      views: {
        'project-mapmodal@project': {
          component: 'rfProjectAnalysisQuickeditPage'
        }
      },
      params: {
        analysis: null,
        onAnalysisUpdate: null
      },
      resolve: {
        analysis: ['$transition$', $transition$ => $transition$.params().analysis],
        onAnalysisUpdate: ['$transition$', $transition$ => $transition$.params().onAnalysisUpdate]
      }
    })
    .state('project.analyses.compare', {
      title: 'Compare Project Analyses',
      url: '/compare?id',
      component: 'rfProjectAnalysesComparePage'
    })
    .state('project.analyses.settings', {
      title: 'Project Analyses Settings',
      url: '/settings',
      component: 'rfProjectAnalysesSettingsPage',
      redirectTo: 'project.analyses.settings.options'
    })
    .state('project.analyses.settings.options', {
      title: 'Project Analyses Options',
      url: '/options',
      component: 'rfProjectAnalysesOptionsPage'
    })
    .state('project.analyses.settings.masking', {
      title: 'Project Analyses Masking',
      url: '/masking',
      component: 'rfProjectAnalysesMaskingPage'
    })
    .state('project.analyses.settings.publishing', {
      title: 'Project Analyses Publishing',
      url: '/publishing',
      component: 'rfProjectAnalysesPublishingPage'
    })
    .state('project.analyses.settings.permissions', {
      title: 'Project Analyses Permissions',
      url: '/permissions',
      component: 'rfProjectAnalysesPermissionsPage'
    })
    .state('project.analyses.visualize', {
      title: 'Project Analyses Visualization',
      url: '/visualize?analysis',
      params: {
        analysis: {
          array: 'auto'
        }
      },
      views: {
        'projectlayernav@root': {
          component: 'rfProjectLayersNav'
        },
        'analyses-visualize@project': {
          component: 'rfProjectAnalysesVisualizePage'
        }
      }
      // component: 'rfProjectAnalysesVisualizePage'
    })
    .state('project.create-analysis', {
      title: 'Create project analysis',
      url: '/create-analysis?page&search',
      component: 'rfProjectCreateAnalysisPage',
      params: {
        layers: {
          array: true
        },
        page: {
          dynamic: true
        },
        search: {
          dynamic: true
        }
      },
      resolve: {
        layers: ['$transition$', $transition$ => $transition$.params().layers]
      }
    })
    // project settings routes
    .state('project.settings.options', {
      title: 'Project Options',
      url: '/options',
      component: 'rfProjectOptionsPage'
    })
    .state('project.settings.publishing', {
      title: 'Project Publishing',
      url: '/publishing',
      component: 'rfProjectPublishingPage'
    })
    .state('project.settings.embed', {
      title: 'Project Embedding',
      url: '/embed',
      component: 'rfProjectEmbedPage'
    })
    .state('project.settings.permissions', {
      title: 'Project Permissions',
      url: '/permissions',
      component: 'rfProjectPermissionsPage'
    });
}

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
      params: { project: null },
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
    .state('projects.edit.scenes', {
      title: 'Project: Scenes',
      url: '/scenes?page',
      templateUrl: projectsScenesTpl,
      controller: 'ProjectsScenesController',
      controllerAs: '$ctrl'
    })
    .state('projects.edit.browse', {
      title: 'Project Scenes',
      url: '/browse/?sceneid' + addScenesQueryParams,
      templateUrl: projectsSceneBrowserTpl,
      controller: 'ProjectsSceneBrowserController',
      controllerAs: '$ctrl',
      reloadOnSearch: false
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
      redirectTo: 'projects.list',
      resolve: projectsModule.resolve
    })
    .state('projects.list', {
      title: 'User Projects',
      url: '/list?page&search&ownership',
      templateUrl: projectsListTpl,
      controller: 'ProjectsListController',
      controllerAs: '$ctrl'
    });
  projectEditStates($stateProvider);
}

function settingsStates($stateProvider) {
  $stateProvider
    .state('user', {
      parent: 'root',
      url: '/user/:userId',
      templateUrl: userTpl,
      controller: 'UserController',
      controllerAs: '$ctrl',
      redirectTo: 'user.projects',
      resolve: userModule.resolve
    })
    .state('user.settings', {
      url: '/settings',
      templateUrl: userSettingsTpl,
      controller: 'SettingsController',
      controllerAs: '$ctrl',
      redirectTo: 'user.settings.profile'
    })
    .state('user.settings.profile', {
      title: 'Profile Settings',
      url: '/profile',
      templateUrl: userSettingsProfileTpl,
      controller: 'ProfileController',
      controllerAs: '$ctrl'
    })
    .state('user.settings.api-tokens', {
      title: 'Settings: API Tokens',
      url: '/api-tokens?code&state',
      templateUrl: userSettingsApiTokensTpl,
      controller: 'ApiTokensController',
      controllerAs: '$ctrl'
    })
    .state('user.settings.map-tokens', {
      title: 'Settings: Map Tokens',
      url: '/map-tokens',
      templateUrl: userSettingsMapTokensTpl,
      controller: 'MapTokensController',
      controllerAs: '$ctrl'
    })
    .state('user.settings.connections', {
      title: 'Settings: API Connections',
      url: '/connections',
      templateUrl: userSettingsConnectionsTpl,
      controller: 'ConnectionsController',
      controllerAs: '$ctrl'
    })
    .state('user.settings.privacy', {
      title: 'Settings: User Privacy',
      url: '/privacy',
      templateUrl: userSettingsPrivacyTpl,
      controller: 'PrivacyController',
      controllerAs: '$ctrl'
    })
    .state('user.settings.notification', {
      title: 'Settings: User Notification',
      url: '/notification',
      templateUrl: userSettingsNotificationTpl,
      controller: 'NotificationController',
      controllerAs: '$ctrl'
    })
    .state('user.organizations', {
      title: 'Organizations',
      url: '/organizations?page',
      templateUrl: userOrganizationsTpl,
      controller: 'UserOrganizationsController',
      controllerAs: '$ctrl'
    })
    .state('user.teams', {
      title: 'Teams',
      url: '/teams?page',
      templateUrl: userTeamsTpl,
      controller: 'UserTeamsController',
      controllerAs: '$ctrl'
    })
    .state('user.projects', {
      title: 'Projects',
      url: '/projects?page&search',
      templateUrl: userProjectsTpl,
      controller: 'UserProjectsController',
      controllerAs: '$ctrl'
    })
    .state('user.rasters', {
      title: 'Rasters',
      url: '/rasters?page',
      templateUrl: userRastersTpl,
      controller: 'UserRastersController',
      controllerAs: '$ctrl'
    });
}

function labStates($stateProvider) {
  $stateProvider
    .state('lab', {
      title: 'Lab',
      template: '<ui-view></ui-view>',
      parent: 'root',
      url: '/lab',
      redirectTo: 'lab.browse'
    })
    // later on we'll use this to view / edit user templates
    .state('lab.template', {
      title: 'View a Template',
      url: '/template/:templateid',
      templateUrl: labTemplateTpl,
      controller: 'LabTemplateController',
      controllerAs: '$ctrl'
    })
    .state('lab.startAnalysis', {
      title: 'Start an analysis',
      url: '/start-analysis/:templateid',
      templateUrl: labStartAnalysisTpl,
      controller: 'LabStartAnalysisController',
      controllerAs: '$ctrl'
    })
    .state('lab.analysis', {
      title: 'Analysis details',
      url: '/analysis/:analysisid',
      views: {
        'navmenu@root': {
          templateUrl: labNavbarTpl,
          controller: 'LabNavbarController',
          controllerAs: '$ctrl'
        },
        '': {
          templateUrl: labAnalysisTpl,
          controller: 'LabAnalysisController',
          controllerAs: '$ctrl'
        }
      },
      resolve: labAnalysisModule.resolve
    })
    .state('lab.browse', {
      url: '/browse',
      templateUrl: labBrowseTpl,
      controller: 'LabBrowseController',
      controllerAs: '$ctrl',
      redirectTo: 'lab.browse.analyses',
      resolve: labBrowseModule.resolve
    })
    .state('lab.browse.templates', {
      title: 'Analysis Search',
      url: '/templates?page&search&query&analysiscategory&analysistag&ownership',
      templateUrl: labBrowseTemplatesTpl,
      controller: 'LabBrowseTemplatesController',
      controllerAs: '$ctrl'
    })
    .state('lab.browse.analyses', {
      title: 'Analyses',
      url: '/analyses?page&search&sort&ownership',
      templateUrl: labBrowseAnalysesTpl,
      controller: 'LabBrowseAnalysesController',
      controllerAs: '$ctrl'
    });
}

function shareStates($stateProvider) {
  $stateProvider.state('share', {
    title: 'Shared Project',
    url: '/share/:projectid',
    templateUrl: shareTpl,
    controller: 'ShareController',
    controllerAs: '$ctrl',
    bypassAuth: true
  });
}

function loginStates($stateProvider) {
  $stateProvider.state('login', {
    title: 'Login',
    url: '/login',
    templateUrl: loginTpl,
    controller: 'LoginController',
    controllerAs: '$ctrl',
    bypassAuth: true
  });
}

function homeStates($stateProvider) {
  $stateProvider.state('home', {
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
      controllerAs: '$ctrl',
      abstract: true,
      resolve: importsModule.resolve
    })
    .state('imports.rasters', {
      title: 'Rasters',
      url: '/rasters?page&ownership',
      templateUrl: rasterListTpl,
      controller: 'RasterListController',
      controllerAs: '$ctrl'
    })
    .state('imports.vectors', {
      title: 'Vectors',
      url: '/vectors?page&search&ownership',
      templateUrl: vectorListTpl,
      controller: 'VectorListController',
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
      url: '/list?page&search&ownership',
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
    });
}

function adminStates($stateProvider) {
  $stateProvider
    .state('admin', {
      parent: 'root',
      title: 'Admin',
      url: '/admin',
      templateUrl: adminTpl,
      controller: 'AdminController',
      controllerAs: '$ctrl'
    })
    .state('admin.organization', {
      title: 'Organization',
      url: '/organization/:organizationId',
      templateUrl: organizationTpl,
      controller: 'OrganizationController',
      controllerAs: '$ctrl',
      resolve: organizationModule.resolve,
      redirectTo: 'admin.organization.projects'
    })
    .state('admin.organization.metrics', {
      title: 'Organization Metrics',
      url: '/metrics',
      templateUrl: organizationMetricsTpl,
      controller: 'OrganizationMetricsController',
      controllerAs: '$ctrl'
    })
    .state('admin.organization.projects', {
      title: 'Organization projects',
      url: '/projects?page&search',
      templateUrl: organizationProjectsTpl,
      controller: 'OrganizationProjectsController',
      controllerAs: '$ctrl'
    })
    .state('admin.organization.rasters', {
      title: 'Organization rasters',
      url: '/rasters?page',
      templateUrl: organizationRastersTpl,
      controller: 'OrganizationRastersController',
      controllerAs: '$ctrl'
    })
    .state('admin.organization.vectors', {
      title: 'Organization vectors',
      url: '/vectors?page&search',
      templateUrl: organizationVectorsTpl,
      controller: 'OrganizationVectorsController',
      controllerAs: '$ctrl'
    })
    .state('admin.organization.datasources', {
      title: 'Organization datasources',
      url: '/datasources?page&search',
      templateUrl: organizationDatasourcesTpl,
      controller: 'OrganizationDatasourcesController',
      controllerAs: '$ctrl'
    })
    .state('admin.organization.templates', {
      title: 'Organization templates',
      url: '/templates?page&search',
      templateUrl: organizationTemplatesTpl,
      controller: 'OrganizationTemplatesController',
      controllerAs: '$ctrl'
    })
    .state('admin.organization.analyses', {
      title: 'Organization analyses',
      url: '/analyses?page&search',
      templateUrl: organizationAnalysesTpl,
      controller: 'OrganizationAnalysesController',
      controllerAs: '$ctrl'
    })
    .state('admin.organization.users', {
      title: 'Organization Users',
      url: '/users?page&search',
      templateUrl: organizationUsersTpl,
      controller: 'OrganizationUsersController',
      controllerAs: '$ctrl'
    })
    .state('admin.organization.teams', {
      title: 'Organization Teams',
      url: '/teams?page&search',
      templateUrl: organizationTeamsTpl,
      controller: 'OrganizationTeamsController',
      controllerAs: '$ctrl'
    })
    .state('admin.organization.settings', {
      title: 'Organization Settings',
      url: '/settings',
      templateUrl: organizationSettingsTpl,
      controller: 'OrganizationSettingsController',
      controllerAs: '$ctrl'
    })
    .state('admin.platform', {
      title: 'Platform',
      url: '/platform/:platformId',
      templateUrl: platformTpl,
      controller: 'PlatformController',
      controllerAs: '$ctrl',
      resolve: platformModule.resolve,
      redirectTo: 'admin.platform.projects'
    })
    .state('admin.platform.projects', {
      title: 'Platform projects',
      url: '/projects?page&search',
      templateUrl: platformProjectsTpl,
      controller: 'PlatformProjectsController',
      controllerAs: '$ctrl'
    })
    .state('admin.platform.rasters', {
      title: 'Platform rasters',
      url: '/rasters?page',
      templateUrl: platformRastersTpl,
      controller: 'PlatformRastersController',
      controllerAs: '$ctrl'
    })
    .state('admin.platform.vectors', {
      title: 'Platform vectors',
      url: '/vectors?page&search',
      templateUrl: platformVectorsTpl,
      controller: 'PlatformVectorsController',
      controllerAs: '$ctrl'
    })
    .state('admin.platform.datasources', {
      title: 'Platform datasources',
      url: '/datasources?page&search',
      templateUrl: platformDatasourcesTpl,
      controller: 'PlatformDatasourcesController',
      controllerAs: '$ctrl'
    })
    .state('admin.platform.templates', {
      title: 'Platform templates',
      url: '/templates?page&search',
      templateUrl: platformTemplatesTpl,
      controller: 'PlatformTemplatesController',
      controllerAs: '$ctrl'
    })
    .state('admin.platform.analyses', {
      title: 'Platform analyses',
      url: '/analyses?page&search',
      templateUrl: platformAnalysesTpl,
      controller: 'PlatformAnalysesController',
      controllerAs: '$ctrl'
    })
    .state('admin.platform.metrics', {
      title: 'Platform Metrics',
      url: '/metrics',
      templateUrl: platformMetricsTpl,
      controller: 'PlatformMetricsController',
      controllerAs: '$ctrl'
    })
    .state('admin.platform.users', {
      title: 'Organization Users',
      url: '/users?page&search',
      templateUrl: platformUsersTpl,
      controller: 'PlatformUsersController',
      controllerAs: '$ctrl'
    })
    .state('admin.platform.settings', {
      url: '/settings',
      title: 'Platform Settings',
      templateUrl: platformSettingsTpl,
      controller: 'PlatformSettingsController',
      controllerAs: '$ctrl',
      abstract: true
    })
    .state('admin.platform.settings.email', {
      url: '/email',
      title: 'Platform Email Settings',
      templateUrl: platformSettingsEmailTpl,
      controller: 'PlatformEmailController',
      controllerAs: '$ctrl'
    })
    .state('admin.platform.organizations', {
      title: 'Platform: Organizations',
      url: '/organizations?page&search',
      templateUrl: platformOrganizationsTpl,
      controller: 'PlatformOrganizationsController',
      controllerAs: '$ctrl'
    })
    .state('admin.team', {
      title: 'Team',
      url: '/team/:teamId',
      templateUrl: teamTpl,
      controller: 'AdminTeamController',
      controllerAs: '$ctrl',
      resolve: teamModule.resolve,
      redirectTo: 'admin.team.projects'
    })
    .state('admin.team.projects', {
      title: 'Team projects',
      url: '/projects?page&search',
      templateUrl: teamProjectsTpl,
      controller: 'TeamProjectsController',
      controllerAs: '$ctrl'
    })
    .state('admin.team.rasters', {
      title: 'Team rasters',
      url: '/rasters?page',
      templateUrl: teamRastersTpl,
      controller: 'TeamRastersController',
      controllerAs: '$ctrl'
    })
    .state('admin.team.vectors', {
      title: 'Team vectors',
      url: '/vectors?page',
      templateUrl: teamVectorsTpl,
      controller: 'TeamVectorsController',
      controllerAs: '$ctrl'
    })
    .state('admin.team.datasources', {
      title: 'Team datasources',
      url: '/datasources?page&search',
      templateUrl: teamDatasourcesTpl,
      controller: 'TeamDatasourcesController',
      controllerAs: '$ctrl'
    })
    .state('admin.team.templates', {
      title: 'Team templates',
      url: '/templates?page&search',
      templateUrl: teamTemplatesTpl,
      controller: 'TeamTemplatesController',
      controllerAs: '$ctrl'
    })
    .state('admin.team.analyses', {
      title: 'Team analyses',
      url: '/analyses?page&search',
      templateUrl: teamAnalysesTpl,
      controller: 'TeamAnalysesController',
      controllerAs: '$ctrl'
    })
    .state('admin.team.users', {
      url: '/users?page&search',
      title: 'Team Members',
      templateUrl: teamUsersTpl,
      controller: 'AdminTeamUsersController',
      controllerAs: '$ctrl'
    });
}

function routeConfig(
  $urlRouterProvider,
  $stateProvider,
  $urlMatcherFactoryProvider,
  $locationProvider
) {
  'ngInject';

  $urlMatcherFactoryProvider.strictMode(false);
  $locationProvider.html5Mode(true);

  $stateProvider
    .state('root', {
      templateUrl: rootTpl,
      controller: 'IndexController'
    })
    .state('callback', {
      url: '/callback'
    });

  loginStates($stateProvider);
  projectStates($stateProvider);
  projectStatesV2($stateProvider);
  settingsStates($stateProvider);
  labStates($stateProvider);
  shareStates($stateProvider);
  shareStatesV2($stateProvider);
  homeStates($stateProvider);
  importStates($stateProvider);
  adminStates($stateProvider);

  $stateProvider.state('error', {
    url: '/error',
    templateUrl: errorTpl,
    controller: 'ErrorController',
    controllerAs: '$ctrl',
    bypassAuth: true
  });

  $urlRouterProvider.otherwise('/home');
}

export default angular.module('index.routes', []).config(routeConfig);

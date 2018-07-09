/* globals window */
import config from './index.config';
import run from './index.run';
import IndexController from './index.controller';

(() => {
    'use strict';
    window.Auth0Lock = require('auth0-lock').default;
})();


const App = angular.module(
    'rasterFoundry', [
        // plugins
        require('angular-ui-router'),
        require('angular-nvd3'),
        'obDateRangePicker',
        'angular-jwt',
        'angular-clipboard',
        'ngAnimate',
        'ngCookies',
        'ngTouch',
        'ngSanitize',
        'ngMessages',
        'ngAria',
        'ngRedux',
        'infinite-scroll',
        'ngResource',
        'oc.lazyLoad',
        'angularLoad',
        'tandibar/ng-rollbar',
        'angular.filter',
        '720kb.tooltips',
        'uuid4',
        'ui.select',
        'ngSanitize',

        // services
        require('./services/services.module').name,

        // components
        require('./index.components').name,

        // routes
        require('./index.routes').name,

        // pages
        require('./pages/home/home.module.js').name,
        require('./pages/login/login.module.js').name,
        require('./pages/error/error.module.js').name,
        require('./pages/share/share.module.js').name,

        require('./pages/lab/analysis/analysis.js').name,
        require('./pages/lab/startAnalysis/startAnalysis.js').name,
        require('./pages/lab/template/template.js').name,

        require('./pages/lab/navbar/navbar.module.js').name,
        require('./pages/lab/browse/browse.js').name,
        require('./pages/lab/browse/analyses/analyses.js').name,
        require('./pages/lab/browse/templates/templates.js').name,

        require('./pages/projects/projects.module.js').name,
        require('./pages/projects/navbar/navbar.module.js').name,
        require('./pages/projects/list/list.module.js').name,
        require('./pages/projects/edit/edit.module.js').name,
        require('./pages/projects/edit/scenes/scenes.module.js').name,
        require('./pages/projects/edit/browse/browse.module.js').name,
        require('./pages/projects/edit/color/color.module.js').name,
        require('./pages/projects/edit/colormode/colormode.module.js').name,
        require('./pages/projects/edit/advancedcolor/advancedcolor.module.js').name,
        require('./pages/projects/edit/advancedcolor/adjust/adjust.module.js').name,
        require('./pages/projects/edit/order/order.module.js').name,
        require('./pages/projects/edit/masking/masking.module.js').name,
        require('./pages/projects/edit/masking/draw/draw.module.js').name,
        require('./pages/projects/edit/aoi-approve/aoi-approve.module.js').name,
        require('./pages/projects/edit/aoi-parameters/aoi-parameters.module.js').name,
        require('./pages/projects/edit/exports/exports.module.js').name,
        require('./pages/projects/edit/exports/new/new.module.js').name,
        require('./pages/projects/edit/annotate/annotate.module.js').name,
        require('./pages/projects/edit/annotate/import/import.module.js').name,
        require('./pages/projects/edit/annotate/export/export.module.js').name,
        require('./pages/projects/edit/sharing/sharing.module.js').name,

        require('./pages/imports/imports.module.js').name,
        require('./pages/imports/raster/raster.module.js').name,
        require('./pages/imports/vector/vector.module.js').name,
        require('./pages/imports/datasources/datasources.module.js').name,
        require('./pages/imports/datasources/list/list.module.js').name,
        require('./pages/imports/datasources/detail/detail.module.js').name,

        require('./pages/user/user.js').name,
        require('./pages/user/projects/projects.js').name,
        require('./pages/user/rasters/rasters.js').name,
        require('./pages/user/settings/settings.module.js').name,
        require('./pages/user/settings/profile/profile.module.js').name,
        require('./pages/user/settings/api/api.module.js').name,
        require('./pages/user/settings/map/map.module.js').name,
        require('./pages/user/settings/connections/connections.module.js').name,
        require('./pages/user/settings/privacy/privacy.module.js').name,
        require('./pages/user/settings/notification/notification.module.js').name,
        require('./pages/user/organizations/organizations.js').name,
        require('./pages/user/teams/teams.js').name,

        require('./pages/admin/admin.module.js').name,
        require('./pages/admin/organization/organization.js').name,
        require('./pages/admin/organization/metrics/metrics.module.js').name,
        require('./pages/admin/organization/teams/teams.module.js').name,
        require('./pages/admin/organization/users/users.module.js').name,
        require('./pages/admin/organization/settings/settings.module.js').name,
        require('./pages/admin/organization/projects/projects.js').name,
        require('./pages/admin/organization/rasters/rasters.js').name,
        require('./pages/admin/organization/vectors/vectors.js').name,
        require('./pages/admin/organization/datasources/datasources.js').name,
        require('./pages/admin/organization/templates/templates.js').name,
        require('./pages/admin/organization/analyses/analyses.js').name,

        require('./pages/admin/platform/platform.module.js').name,
        require('./pages/admin/platform/projects/projects.js').name,
        require('./pages/admin/platform/rasters/rasters.js').name,
        require('./pages/admin/platform/vectors/vectors.js').name,
        require('./pages/admin/platform/datasources/datasources.js').name,
        require('./pages/admin/platform/templates/templates.js').name,
        require('./pages/admin/platform/analyses/analyses.js').name,
        require('./pages/admin/platform/metrics/metrics.module.js').name,
        require('./pages/admin/platform/users/users.module.js').name,
        require('./pages/admin/platform/settings/settings.module.js').name,
        require('./pages/admin/platform/settings/email/email.module.js').name,
        require('./pages/admin/platform/organizations/organizations.module.js').name,
        require('./pages/admin/platform/organizations/detail/detail.module.js').name,
        require('./pages/admin/platform/organizations/detail/features/features.js').name,
        require('./pages/admin/platform/organizations/detail/limits/limits.js').name,
        require('./pages/admin/platform/organizations/detail/settings/settings.js').name,

        require('./pages/admin/team/team.js').name,
        require('./pages/admin/team/users/users.js').name,
        require('./pages/admin/team/projects/projects.js').name,
        require('./pages/admin/team/rasters/rasters.js').name,
        require('./pages/admin/team/vectors/vectors.js').name,
        require('./pages/admin/team/datasources/datasources.js').name,
        require('./pages/admin/team/templates/templates.js').name,
        require('./pages/admin/team/analyses/analyses.js').name
    ]
);

App.controller('IndexController', IndexController);

App.config(config)
    .run(run);

export default App;

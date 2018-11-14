/* globals window */
import vendors from './index.vendor.js';
import config from './index.config';
import run from './index.run';
import IndexController from './index.controller';

(() => {
    'use strict';
    window.Auth0Lock = require('auth0-lock').default;
})();
import angular from 'angular';

require('ui-select/dist/select.css');
require('angularjs-slider/dist/rzslider.css');

const App = angular.module(
    'rasterFoundry', [
        // plugins
        'ui.router',
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
        // 'oc.lazyLoad',
        'angularLoad',
        'tandibar/ng-rollbar',
        'angular.filter',
        '720kb.tooltips',
        'uuid4',
        'ui.select',
        'ngSanitize',

        // services
        require('./services/services.module').default.name,

        // components
        require('./index.components').default.name,

        // routes
        require('./index.routes').default.name,

        // pages
        require('./pages/home/home.module.js').default.name,
        require('./pages/login/login.module.js').default.name,
        require('./pages/error/error.module.js').default.name,
        require('./pages/share/share.module.js').default.name,

        require('./pages/lab/analysis/analysis.js').default.name,
        require('./pages/lab/startAnalysis/startAnalysis.js').default.name,
        require('./pages/lab/template/template.js').default.name,

        require('./pages/lab/navbar/navbar.module.js').default.name,
        require('./pages/lab/browse/browse.js').default.name,
        require('./pages/lab/browse/analyses/analyses.js').default.name,
        require('./pages/lab/browse/templates/templates.js').default.name,

        require('./pages/projects/projects.module.js').default.name,
        require('./pages/projects/navbar/navbar.module.js').default.name,
        require('./pages/projects/list/list.module.js').default.name,
        require('./pages/projects/edit/edit.module.js').default.name,
        require('./pages/projects/edit/scenes/scenes.module.js').default.name,
        require('./pages/projects/edit/browse/browse.module.js').default.name,
        require('./pages/projects/edit/color/color.module.js').default.name,
        require('./pages/projects/edit/colormode/colormode.module.js').default.name,
        require('./pages/projects/edit/advancedcolor/advancedcolor.module.js').default.name,
        require('./pages/projects/edit/advancedcolor/adjust/adjust.module.js').default.name,
        require('./pages/projects/edit/order/order.module.js').default.name,
        require('./pages/projects/edit/masking/masking.module.js').default.name,
        require('./pages/projects/edit/masking/draw/draw.module.js').default.name,
        require('./pages/projects/edit/aoi-approve/aoi-approve.module.js').default.name,
        require('./pages/projects/edit/aoi-parameters/aoi-parameters.module.js').default.name,
        require('./pages/projects/edit/exports/exports.module.js').default.name,
        require('./pages/projects/edit/exports/new/new.module.js').default.name,
        require('./pages/projects/edit/annotate/annotate.module.js').default.name,
        require('./pages/projects/edit/annotate/import/import.module.js').default.name,
        require('./pages/projects/edit/annotate/export/export.module.js').default.name,
        require('./pages/projects/edit/sharing/sharing.module.js').default.name,

        require('./pages/imports/imports.module.js').default.name,
        require('./pages/imports/raster/raster.module.js').default.name,
        require('./pages/imports/vector/vector.module.js').default.name,
        require('./pages/imports/datasources/datasources.module.js').default.name,
        require('./pages/imports/datasources/list/list.module.js').default.name,
        require('./pages/imports/datasources/detail/detail.module.js').default.name,

        require('./pages/user/user.js').default.name,
        require('./pages/user/projects/projects.js').default.name,
        require('./pages/user/rasters/rasters.js').default.name,
        require('./pages/user/settings/settings.module.js').default.name,
        require('./pages/user/settings/profile/profile.module.js').default.name,
        require('./pages/user/settings/api/api.module.js').default.name,
        require('./pages/user/settings/map/map.module.js').default.name,
        require('./pages/user/settings/connections/connections.module.js').default.name,
        require('./pages/user/settings/privacy/privacy.module.js').default.name,
        require('./pages/user/settings/notification/notification.module.js').default.name,
        require('./pages/user/organizations/organizations.js').default.name,
        require('./pages/user/teams/teams.js').default.name,

        require('./pages/admin/admin.module.js').default.name,
        require('./pages/admin/organization/organization.js').default.name,
        require('./pages/admin/organization/metrics/metrics.module.js').default.name,
        require('./pages/admin/organization/teams/teams.module.js').default.name,
        require('./pages/admin/organization/users/users.module.js').default.name,
        require('./pages/admin/organization/settings/settings.module.js').default.name,
        require('./pages/admin/organization/projects/projects.js').default.name,
        require('./pages/admin/organization/rasters/rasters.js').default.name,
        require('./pages/admin/organization/vectors/vectors.js').default.name,
        require('./pages/admin/organization/datasources/datasources.js').default.name,
        require('./pages/admin/organization/templates/templates.js').default.name,
        require('./pages/admin/organization/analyses/analyses.js').default.name,

        require('./pages/admin/platform/platform.module.js').default.name,
        require('./pages/admin/platform/projects/projects.js').default.name,
        require('./pages/admin/platform/rasters/rasters.js').default.name,
        require('./pages/admin/platform/vectors/vectors.js').default.name,
        require('./pages/admin/platform/datasources/datasources.js').default.name,
        require('./pages/admin/platform/templates/templates.js').default.name,
        require('./pages/admin/platform/analyses/analyses.js').default.name,
        require('./pages/admin/platform/metrics/metrics.module.js').default.name,
        require('./pages/admin/platform/users/users.module.js').default.name,
        require('./pages/admin/platform/settings/settings.module.js').default.name,
        require('./pages/admin/platform/settings/email/email.module.js').default.name,
        require('./pages/admin/platform/organizations/organizations.module.js').default.name,
        require('./pages/admin/platform/organizations/detail/detail.module.js').default.name,
        require('./pages/admin/platform/organizations/detail/features/features.js').default.name,
        require('./pages/admin/platform/organizations/detail/limits/limits.js').default.name,
        require('./pages/admin/platform/organizations/detail/settings/settings.js').default.name,

        require('./pages/admin/team/team.js').default.name,
        require('./pages/admin/team/users/users.js').default.name,
        require('./pages/admin/team/projects/projects.js').default.name,
        require('./pages/admin/team/rasters/rasters.js').default.name,
        require('./pages/admin/team/vectors/vectors.js').default.name,
        require('./pages/admin/team/datasources/datasources.js').default.name,
        require('./pages/admin/team/templates/templates.js').default.name,
        require('./pages/admin/team/analyses/analyses.js').default.name
    ]
);

App.controller('IndexController', IndexController);

App.config(config)
    .run(run);

export default App;

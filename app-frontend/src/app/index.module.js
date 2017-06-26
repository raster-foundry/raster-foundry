/* globals window */
import config from './index.config';
import run from './index.run';
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
        'infinite-scroll',
        'ngResource',
        'oc.lazyLoad',
        'angularLoad',
        'tandibar/ng-rollbar',
        'angular.filter',
        '720kb.tooltips',

        // core
        require('./core/core.module').name,

        // components
        require('./index.components').name,

        // routes
        require('./index.routes').name,

        // pages
        require('./pages/home/home.module.js').name,
        require('./pages/login/login.module.js').name,
        require('./pages/error/error.module.js').name,
        require('./pages/share/share.module.js').name,

        require('./pages/lab/lab.module.js').name,
        require('./pages/lab/edit/edit.module.js').name,
        require('./pages/lab/run/run.module.js').name,

        require('./pages/market/market.module.js').name,
        require('./pages/market/search/search.module.js').name,
        require('./pages/market/tool/tool.module.js').name,

        require('./pages/projects/projects.module.js').name,
        require('./pages/projects/navbar/navbar.module.js').name,
        require('./pages/projects/list/list.module.js').name,
        require('./pages/projects/detail/detail.module.js').name,
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
        require('./pages/projects/edit/aoi-parameters/draw-aoi/draw-aoi.module.js').name,
        require('./pages/projects/edit/export/export.module.js').name,

        require('./pages/imports/imports.module.js').name,
        require('./pages/imports/datasources/datasources.module.js').name,
        require('./pages/imports/datasources/list/list.module.js').name,
        require('./pages/imports/datasources/detail/detail.module.js').name,
        require('./pages/imports/datasources/detail/colorComposites/colorComposites.module').name,

        require('./pages/settings/settings.module.js').name,
        require('./pages/settings/profile/profile.module.js').name,
        require('./pages/settings/tokens/tokens.module.js').name,
        require('./pages/settings/tokens/api/api.module.js').name,
        require('./pages/settings/tokens/map/map.module.js').name,
        require('./pages/settings/connections/connections.module.js').name
    ]
);

App.config(config)
    .run(run);

export default App;

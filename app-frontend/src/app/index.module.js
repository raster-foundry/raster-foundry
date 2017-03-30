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
        'auth0.lock',
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

        // core
        require('./core/core.module').name,

        // components
        require('./index.components').name,

        // routes
        require('./index.routes').name,

        // pages
        require('./pages/login/login.module.js').name,
        require('./pages/browse/browse.module.js').name,
        require('./pages/lab/lab.module.js').name,
        require('./pages/lab/edit/edit.module.js').name,
        require('./pages/lab/run/run.module.js').name,
        require('./pages/market/market.module.js').name,
        require('./pages/market/search/search.module.js').name,
        require('./pages/market/tool/tool.module.js').name,
        require('./pages/editor/editor.module.js').name,
        require('./pages/editor/project/projectEdit.module.js').name,
        require('./pages/library/library.module.js').name,
        require('./pages/library/scenes/scenes.module.js').name,
        require('./pages/library/scenes/list/list.module.js').name,
        require('./pages/library/scenes/detail/detail.module.js').name,
        require('./pages/library/projects/projects.module.js').name,
        require('./pages/library/projects/list/list.module.js').name,
        require('./pages/library/projects/detail/detail.module.js').name,
        require('./pages/library/projects/detail/scene/scene.module.js').name,
        require('./pages/library/projects/detail/projectScenes/projectScenes.module.js').name,
        require('./pages/settings/settings.module.js').name,
        require('./pages/share/share.module.js').name,
        require('./pages/settings/profile/profile.module.js').name,
        require('./pages/settings/account/account.module.js').name,
        require('./pages/settings/tokens/tokens.module.js').name,
        require('./pages/settings/tokens/api/api.module.js').name,
        require('./pages/settings/tokens/map/map.module.js').name,
        require('./pages/error/error.module.js').name,
        require('./pages/home/home.module.js').name,
        require('./pages/imports/imports.module.js').name,
        require('./pages/imports/datasources/datasources.module.js').name,
        require('./pages/imports/datasources/list/list.module.js').name,
        require('./pages/imports/datasources/detail/detail.module.js').name,
        require('./pages/imports/datasources/detail/colorComposites/colorComposites.module').name,
        require('./pages/imports/datasources/detail/colorCorrection/colorCorrection.module').name
    ]
);

App.config(config)
    .run(run);

export default App;

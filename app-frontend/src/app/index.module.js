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
        'angular-storage',
        'angular-jwt',
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

        // core
        require('./core/core.module').name,

        // components
        require('./index.components').name,

        // routes
        require('./index.routes').name,

        // pages
        require('./pages/browse/browse.module.js').name,
        require('./pages/market/market.module.js').name,
        require('./pages/market/search/search.module.js').name,
        require('./pages/market/model/model.module.js').name,
        require('./pages/library/library.module.js').name,
        require('./pages/library/scenes/scenes.module.js').name,
        require('./pages/library/scenes/list/list.module.js').name,
        require('./pages/library/scenes/detail/detail.module.js').name,
        require('./pages/library/buckets/buckets.module.js').name,
        require('./pages/library/buckets/list/list.module.js').name,
        require('./pages/library/buckets/detail/detail.module.js').name,
        require('./pages/library/buckets/detail/scene/scene.module.js').name,
        require('./pages/library/buckets/detail/bucketScenes/bucketScenes.module.js').name,
        require('./pages/settings/settings.module.js').name,
        require('./pages/settings/profile/profile.module.js').name,
        require('./pages/settings/account/account.module.js').name,
        require('./pages/settings/tokens/tokens.module.js').name,
        require('./pages/error/error.module.js').name

    ]
);

App.config(config)
    .run(run);

export default App;

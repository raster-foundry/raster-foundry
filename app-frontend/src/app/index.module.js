import config from './index.config';
import run from './index.run';

const App = angular.module(
    'rasterFoundry', [
        // plugins
        require('angular-ui-router'),
        'angular-storage',
        'angular-jwt',
        'auth0',
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
        require('./pages/library/library.module.js').name,
        require('./pages/library/scenes/scenes.module.js').name,
        require('./pages/library/scenes/list/list.module.js').name,
        require('./pages/library/scenes/detail/detail.module.js').name,
        require('./pages/library/buckets/buckets.module.js').name,
        require('./pages/settings/settings.module.js').name,
        require('./pages/settings/profile/profile.module.js').name,
        require('./pages/settings/account/account.module.js').name,
        require('./pages/error/error.module.js').name

    ]
);

App.config(config)
    .run(run);

export default App;

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
        'ngResource',
        'oc.lazyLoad',

        // core
        require('./core/core.module').name,

        // components
        require('./index.components').name,

        // routes
        require('./index.routes').name,

        // pages
        require('./pages/main/main.module').name,
        require('./pages/login/login.module').name,
        require('./pages/library/library.module').name,
        require('./pages/library/project/project.module').name,
        require('./pages/account/account.module').name,
        require('./pages/account/billing/billing.module').name,
        require('./pages/account/keys/keys.module').name
    ]
);

App.config(config)
    .run(run);

export default App;

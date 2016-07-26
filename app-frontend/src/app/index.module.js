import angular from 'angular';

// import * as components from './index.components';
import config from './index.config';
import run from './index.run';


const App = angular.module(
    'rasterFoundry', [
        // plugins
        require('angular-ui-router'),
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
        require('./pages/login/login.module').name
    ]
);

App.config(config)
    .run(run);

export default App;

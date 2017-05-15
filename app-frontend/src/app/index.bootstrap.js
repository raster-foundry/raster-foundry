/* global document */

// index.html page to dist folder
import '!!file-loader?name=[name].[ext]!../favicon.ico';

// main App module
import './index.module';

import '../assets/styles/sass/app.scss';

import deferredBootstrapper from 'angular-deferred-bootstrap';

angular.element(document).ready(function () {
    deferredBootstrapper.bootstrap({
        element: document,
        module: 'rasterFoundry',
        bootstrapConfig: {
            strictDi: true
        },
        resolve: {
            APP_CONFIG: ['$http', ($http) => {
                return $http.get('/config').then(
                    (result) => result,
                    (error) => ({error: error})
                );
            }]
        }
    });
});
